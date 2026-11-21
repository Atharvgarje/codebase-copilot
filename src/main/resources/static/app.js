const API = '/api/v1';
const state = { projects: [], selected: null, jobs: [], currentJobId: null, answer: null, toastTimer: null };

const el = (id) => document.getElementById(id);
const projectList = el('repo-list');
const questionInput = el('question-input');

async function api(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
  });
  const text = await response.text();
  let payload = null;
  if (text) {
    try { payload = JSON.parse(text); } catch { payload = { message: text }; }
  }
  if (!response.ok) {
    const error = new Error(payload?.message || `Request failed (${response.status})`);
    error.code = payload?.code;
    error.status = response.status;
    throw error;
  }
  return payload;
}

function toast(message, isError = false) {
  const node = el('toast');
  node.textContent = message;
  node.classList.toggle('error', isError);
  node.classList.add('visible');
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => node.classList.remove('visible'), 4200);
}

function shortRepo(url) {
  try { return new URL(url).pathname.replace(/\.git$/, '').replace(/^\//, ''); }
  catch { return url; }
}

function renderProjects() {
  el('repo-count').textContent = String(state.projects.length);
  projectList.replaceChildren();
  if (state.projects.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'empty-repo';
    empty.textContent = 'No repositories yet';
    projectList.append(empty);
    return;
  }
  for (const project of state.projects) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `repo-item${state.selected?.id === project.id ? ' active' : ''}`;
    button.setAttribute('aria-current', state.selected?.id === project.id ? 'page' : 'false');
    const icon = document.createElement('span');
    icon.className = 'repo-icon';
    icon.textContent = '</>';
    const copy = document.createElement('span');
    copy.className = 'repo-copy';
    const name = document.createElement('strong');
    name.textContent = project.name;
    const repo = document.createElement('small');
    repo.textContent = shortRepo(project.repositoryUrl);
    copy.append(name, repo);
    button.append(icon, copy);
    button.addEventListener('click', () => selectProject(project));
    projectList.append(button);
  }
}

function setStatus(status, label) {
  const pill = el('index-status');
  pill.className = `status-pill status-${status}`;
  el('index-status-text').textContent = label;
}

function setQuestionAvailability(available, hint) {
  questionInput.disabled = !available;
  el('ask-button').disabled = !available;
  const hintNode = el('question-hint');
  hintNode.replaceChildren();
  const icon = document.createElement('span');
  icon.className = 'hint-icon';
  icon.textContent = '↳';
  hintNode.append(icon, document.createTextNode(` ${hint}`));
  el('suggestions').hidden = !available;
}

function renderSelectedProject() {
  const project = state.selected;
  el('empty-state').hidden = Boolean(project);
  el('workspace-card').hidden = !project;
  el('question-card').hidden = !project;
  if (!project) {
    el('selected-project-name').textContent = 'Choose a repository';
    el('index-button').disabled = true;
    setStatus('idle', 'No project selected');
    setQuestionAvailability(false, 'Select an indexed repository to start asking');
    el('project-meta').hidden = true;
    return;
  }
  el('selected-project-name').textContent = project.name;
  const url = el('selected-project-url');
  url.textContent = project.repositoryUrl;
  url.href = project.repositoryUrl;
  el('branch-value').textContent = project.defaultBranch;
  el('project-meta').hidden = false;
  el('index-button').disabled = Boolean(state.currentJobId);
  updateProjectStatus();
}

function updateProjectStatus() {
  const active = state.jobs.find((job) => job.status === 'QUEUED' || job.status === 'RUNNING');
  const completed = state.jobs.find((job) => job.status === 'SUCCEEDED');
  const failed = state.jobs.find((job) => job.status === 'FAILED');
  el('index-button').disabled = Boolean(active);
  el('job-value').textContent = active ? `Attempt ${active.attempt || 0}` : completed ? 'Ready' : failed ? 'Needs retry' : 'Not indexed';
  if (active) {
    setStatus('running', active.status === 'QUEUED' ? 'Index queued' : 'Indexing code');
    setQuestionAvailability(Boolean(completed), completed ? 'Indexing a new revision. Your existing index is still available.' : 'Your code is indexing. You can ask questions as soon as it finishes.');
  } else if (completed) {
    setStatus('ready', 'Index ready');
    setQuestionAvailability(true, 'Answers are generated locally and grounded in source citations');
  } else if (failed) {
    setStatus('failed', 'Index failed');
    setQuestionAvailability(false, `Indexing failed (${failed.errorCode || 'unknown error'}). Retry indexing to continue.`);
  } else {
    setStatus('idle', 'Not indexed yet');
    setQuestionAvailability(false, 'Index this repository before asking questions');
  }
}

async function selectProject(project) {
  state.selected = project;
  state.jobs = [];
  state.currentJobId = null;
  state.answer = null;
  el('answer-card').hidden = true;
  renderProjects();
  renderSelectedProject();
  try {
    state.jobs = await api(`/projects/${project.id}/index-jobs`);
    if (state.selected?.id !== project.id) return;
    updateProjectStatus();
    const newestActive = state.jobs.find((job) => job.status === 'QUEUED' || job.status === 'RUNNING');
    if (newestActive) watchJob(newestActive, project.id);
  } catch (error) {
    toast(error.message, true);
  }
}

async function loadProjects() {
  try {
    const page = await api('/projects?page=0&size=100');
    state.projects = page.items || [];
    renderProjects();
    el('repo-list-state').textContent = '';
    const selected = state.projects.find((project) => project.id === state.selected?.id) || state.projects[0];
    if (selected) await selectProject(selected);
    else renderSelectedProject();
  } catch (error) {
    el('repo-list-state').textContent = 'OFFLINE';
    projectList.replaceChildren();
    const message = document.createElement('div');
    message.className = 'empty-repo';
    message.textContent = 'Could not reach the API. Is the app running?';
    projectList.append(message);
    setStatus('failed', 'API unavailable');
    toast(error.message, true);
  }
}

el('project-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const submit = event.submitter;
  if (submit) submit.disabled = true;
  const body = {
    name: el('project-name').value.trim(),
    repositoryUrl: el('repository-url').value.trim(),
    defaultBranch: el('default-branch').value.trim() || 'main',
  };
  try {
    const project = await api('/projects', { method: 'POST', body: JSON.stringify(body) });
    state.projects.unshift(project);
    state.projects = state.projects.filter((item, index, items) => items.findIndex((candidate) => candidate.id === item.id) === index);
    el('project-form').reset();
    el('default-branch').value = 'main';
    await selectProject(project);
    renderProjects();
    toast('Repository connected. Index it when you’re ready.');
  } catch (error) {
    toast(error.message, true);
  } finally {
    if (submit) submit.disabled = false;
  }
});

async function watchJob(job, projectId) {
  state.currentJobId = job.id;
  updateProjectStatus();
  while (state.selected?.id === projectId) {
    await new Promise((resolve) => setTimeout(resolve, 2500));
    try {
      const latest = await api(`/index-jobs/${job.id}`);
      if (state.selected?.id !== projectId) return;
      state.jobs = [latest, ...state.jobs.filter((item) => item.id !== latest.id)];
      const done = latest.status !== 'QUEUED' && latest.status !== 'RUNNING';
      if (done) state.currentJobId = null;
      updateProjectStatus();
      if (done) {
        toast(latest.status === 'SUCCEEDED' ? 'Repository indexed. Ask away.' : `Indexing failed: ${latest.errorCode || 'unknown error'}`, latest.status !== 'SUCCEEDED');
        return;
      }
    } catch (error) {
      state.currentJobId = null;
      updateProjectStatus();
      toast(`Could not check indexing status: ${error.message}`, true);
      return;
    }
  }
}

el('index-button').addEventListener('click', async () => {
  if (!state.selected) return;
  const project = state.selected;
  el('index-button').disabled = true;
  try {
    const job = await api(`/projects/${project.id}/index-jobs`, { method: 'POST' });
    state.jobs = [job, ...state.jobs.filter((item) => item.id !== job.id)];
    state.currentJobId = job.id;
    updateProjectStatus();
    toast('Index job queued. You can keep this page open to follow progress.');
    watchJob(job, project.id);
  } catch (error) {
    el('index-button').disabled = false;
    toast(error.message, true);
  }
});

function renderAnswer(result) {
  state.answer = result;
  el('answer-card').hidden = false;
  el('answer-body').textContent = result.answer;
  const footer = el('answer-footer');
  footer.replaceChildren();
  const addMetric = (text) => { const chip = document.createElement('span'); chip.textContent = text; footer.append(chip); };
  addMetric(result.model || 'Local model');
  if (result.totalTokens != null) addMetric(`${result.totalTokens} tokens`);
  if (result.indexedRevision) addMetric(`revision ${result.indexedRevision.slice(0, 8)}`);
  const citations = el('citations-list');
  citations.replaceChildren();
  for (const item of result.citations || []) {
    const details = document.createElement('details');
    details.className = 'citation';
    const summary = document.createElement('summary');
    const number = document.createElement('span');
    number.className = 'citation-number';
    number.textContent = String(item.number);
    const path = document.createElement('span');
    path.className = 'citation-path';
    path.textContent = item.filePath;
    const lines = document.createElement('span');
    lines.className = 'citation-lines';
    lines.textContent = `L${item.startLine}–${item.endLine}`;
    summary.append(number, path, lines);
    const snippet = document.createElement('pre');
    snippet.textContent = item.snippet;
    details.append(summary, snippet);
    citations.append(details);
  }
  el('citation-count').textContent = `${(result.citations || []).length} SOURCES`;
  el('answer-card').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

el('question-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!state.selected) return;
  const question = questionInput.value.trim();
  if (!question) return;
  const button = el('ask-button');
  button.disabled = true;
  button.innerHTML = '<span class="loading-dots">•••</span> Thinking locally';
  try {
    const result = await api(`/projects/${state.selected.id}/questions`, {
      method: 'POST', body: JSON.stringify({ question }),
    });
    renderAnswer(result);
  } catch (error) {
    toast(error.code === 'AI_PROVIDER_UNAVAILABLE'
      ? 'The local model did not finish. Check Ollama is running, then try again.'
      : error.message, true);
  } finally {
    button.innerHTML = 'Ask Copilot <span aria-hidden="true">→</span>';
    button.disabled = !state.jobs.some((job) => job.status === 'SUCCEEDED');
  }
});

document.querySelectorAll('.suggestion').forEach((button) => {
  button.addEventListener('click', () => {
    questionInput.value = button.dataset.question;
    questionInput.focus();
  });
});

el('copy-answer').addEventListener('click', async () => {
  if (!state.answer) return;
  try { await navigator.clipboard.writeText(state.answer.answer); toast('Answer copied to clipboard.'); }
  catch { toast('Clipboard access is unavailable in this browser.', true); }
});

loadProjects();
