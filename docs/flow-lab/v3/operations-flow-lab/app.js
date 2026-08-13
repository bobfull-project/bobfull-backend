/* Playback and rendering only. Every visual decision comes from step.visual. */
const $ = (id) => document.getElementById(id);
const state = { chapter: 0, scenario: 0, step: 0, mode: "presentation", timer: null };
const currentChapter = () => chapters[state.chapter];
const currentScenario = () => currentChapter().scenarios[state.scenario];
const currentStep = () => currentScenario().steps[state.step];
const statusClass = (value) => ({ [FACT.VERIFIED]: "verified", [FACT.MEASURED]: "measured", [FACT.DESIGN]: "design", [FACT.FUTURE]: "future", [FACT.MERGED]: "merged" }[value]);
const format = (value) => value == null ? "not applicable" : value;
const topology = {
  nodes: [["client", "Client"], ["web", "Web / STOMP"], ["app", "Application"], ["db", "DB"], ["outbox", "Outbox"], ["kafka", "Kafka"], ["consumer", "AI Consumer"], ["llm", "LLM"], ["redis", "Redis Pub/Sub"], ["app-a", "App A"], ["app-b", "App B"], ["stomp", "Local STOMP"]],
  nodePositions: { client:[25,190], web:[180,190], app:[335,190], db:[500,190], outbox:[670,35], kafka:[825,35], consumer:[980,35], llm:[1135,35], redis:[670,350], "app-a":[850,290], "app-b":[850,390], stomp:[1050,340] },
  edges: {
    request:"M125 225 H180", "request-app":"M280 225 H335", persist:"M435 225 H500",
    "outbox-write":"M600 225 H630 V70 H670", "outbox-claim":"M670 70 H630 V160 H435", "outbox-complete":"M435 160 H630 V70 H670",
    "outbox-publish":"M770 70 H825", "kafka-consume":"M925 70 H980", "ai-call":"M1080 70 H1135", "kafka-dlt":"M925 105 H950 V145 H980",
    "redis-publish":"M600 225 H630 V385 H670", "redis-app-a":"M770 385 H810 V325 H850", "redis-app-b":"M770 385 H850", "local-stomp":"M950 325 H1000 V375 H1050", "local-stomp-b":"M950 425 H1000 V375 H1050"
  },
  labels: { request:[135,180], "request-app":[285,180], persist:[440,180], "outbox-write":[620,112], "outbox-publish":[775,24], "kafka-consume":[930,24], "ai-call":[1085,24], "redis-publish":[620,332], "redis-app-a":[780,286], "redis-app-b":[780,402], "local-stomp":[970,305], "local-stomp-b":[970,445] }
};
function populateSelects() { $("chapterSelect").innerHTML = chapters.map((item, i) => `<option value="${i}">${item.title}</option>`).join(""); $("chapterSelect").value = state.chapter; $("scenarioSelect").innerHTML = currentChapter().scenarios.map((item, i) => `<option value="${i}">${item.title}</option>`).join(""); $("scenarioSelect").value = state.scenario; }
function renderCanvas(data) {
  const v = data.visual;
  const edgeSvg = Object.entries(topology.edges).map(([id, path]) => `<path id="edge-${id}" class="connector ${v.activeEdges.includes(id) ? "active" : "dim"}" d="${path}"/>`).join("");
  const tokenSvg = v.activeEdges.map((id) => tokenSvgFor(id, v.token)).join("");
  const labelSvg = v.activeEdges.map((id) => edgeLabelSvg(id, v.token)).join("");
  const nodesSvg = topology.nodes.map(([id, label]) => {
    const [x, y] = topology.nodePositions[id], active = v.activeNodes.includes(id);
    return `<g class="canvas-node ${active ? "active" : "dim"}" transform="translate(${x} ${y})"><rect width="100" height="70" rx="10"/><text x="50" y="42">${label}</text></g>`;
  }).join("");
  $("canvas").innerHTML = `<svg class="topology" viewBox="0 0 1260 470" role="img" aria-label="Client에서 DB로 들어온 요청이 Outbox Kafka AI와 Redis Pub Sub 실시간 fan-out으로 분기하는 고정 시스템 topology"><g class="connectors">${edgeSvg}</g><g class="edge-labels">${labelSvg}</g><g class="tokens">${tokenSvg}</g><g class="nodes">${nodesSvg}</g></svg><div class="flow-outcome ${v.outcome || ""}">${v.outcome ? `${outcomeLabel(v.outcome)} · ${data.action}` : ""}</div>`;
}
function tokenSvgFor(edgeId, kind) {
  const shape = kind === "event" ? "<rect x=\"-5\" y=\"-5\" width=\"10\" height=\"10\" transform=\"rotate(45)\"/>" : kind === "broadcast" ? "<path d=\"M-6 -5 L6 0 L-6 5 Z\"/>" : kind === "retry" ? "<text y=\"5\">↻</text>" : kind === "failure" ? "<text y=\"5\">×</text>" : kind === "dlt" ? "<text y=\"5\">↓</text>" : "<circle r=\"5\"/>";
  return `<g class="token ${kind || "event"}">${shape}<animateMotion dur="1.25s" repeatCount="indefinite" path="${topology.edges[edgeId]}"/></g>`;
}
function edgeLabelSvg(edgeId, kind) {
  const position = topology.labels[edgeId]; if (!position || !kind) return "";
  const [x, y] = position; return `<g class="edge-label" transform="translate(${x} ${y})"><rect x="-4" y="-12" width="${tokenLabel(kind).length * 6 + 10}" height="18" rx="5"/><text x="1" y="1">${tokenLabel(kind)}</text></g>`;
}
function tokenLabel(token) { return ({ request: "● request", event: "◆ event", commit: "✓ commit", retry: "↻ retry", failure: "× failure", dlt: "↓ DLT", broadcast: "↠ broadcast" })[token] || ""; }
function outcomeLabel(outcome) { return ({ committed: "✓ committed", acknowledged: "✓ broker ACK", completed: "✓ completed", delivered: "↠ delivered", failure: "× failure", dlt: "↓ DLT", "not verified": "? not verified" })[outcome] || outcome; }
function renderComparison(data) {
  const element = $("comparison"); element.hidden = !currentScenario().comparison; if (element.hidden) return;
  const lanes = data.comparison;
  element.innerHTML = `<article class="lane"><span class="badge verified">V2 BEFORE — baseline Evidence</span><h2>AFTER_COMMIT</h2><p>${lanes.v2}</p></article><article class="lane"><span class="badge verified">V3 AFTER — Transactional Outbox</span><h2>Outbox</h2><p>${lanes.v3}</p></article>`;
}
function linked(refs) { return refs.length ? refs.map((item) => `<a href="${item.href}" target="_blank" rel="noreferrer">${item.label}</a>`).join("<br>") : "not applicable"; }
function renderDetails(data) {
  const entries = [["Why", data.narration], ["Code", data.codeReferences.length ? data.codeReferences.join(" · ") : "not applicable"], ["Runtime", "코드·Evidence 기반 정적 시뮬레이션 — 라이브 JVM이 아님"], ["Transaction / Lock", `Transaction: ${format(data.transaction)}<br>Lock: ${format(data.lock)}`], ["Event / Infra", `Outbox: ${format(data.outbox)}<br>Kafka: ${format(data.kafka)}<br>Consumer: ${format(data.consumer)}<br>Redis: ${format(data.redis)}`], ["Logs / Metrics", `Logs: ${format(data.logs)}<br>Metrics: ${format(data.metrics)}`], ["Evidence", linked(data.evidenceReferences)], ["Limits", format(data.limits)]];
  $("detailGrid").innerHTML = entries.map(([title, value]) => `<article><h3>${title}</h3><p>${value}</p></article>`).join("");
}
function renderOps(data) { $("opsGrid").innerHTML = [["Structured log", data.logs], ["Metric", data.metrics], ["Evidence", linked(data.evidenceReferences)], ["Limit", data.limits]].map(([title, value]) => `<article class="${value == null ? "na" : ""}"><h3>${title}</h3><p>${format(value)}</p></article>`).join(""); }
function render() {
  const data = currentStep(); populateSelects(); renderCanvas(data); renderComparison(data); renderDetails(data); renderOps(data);
  $("stepTitle").textContent = data.action; $("narration").textContent = data.narration; $("counter").textContent = `Step ${state.step + 1} / ${currentScenario().steps.length}`;
  $("factBadge").textContent = data.factStatus; $("factBadge").className = `badge ${statusClass(data.factStatus)}`; $("stepFact").innerHTML = `<span class="badge ${statusClass(data.factStatus)}">${data.factStatus}</span>${quickState(data)}<p>${data.limits || "Evidence 범위 안에서 표시"}</p>`;
  const cards = [["Domain", data.domainState], ["Transaction", data.transaction], ["Outbox", data.outbox], ["Kafka / Consumer", [data.kafka, data.consumer].filter(Boolean).join(" / ") || null], ["Redis", data.redis], ["Outcome", data.visual.outcome]];
  $("stateGrid").innerHTML = cards.map(([title, value]) => `<article class="${value == null ? "na" : ""}"><h3>${title}</h3><p>${format(value)}</p></article>`).join("");
  $("learningPanel").hidden = state.mode !== "learning"; $("opsPanel").hidden = state.mode !== "ops"; document.querySelectorAll("[data-mode]").forEach((button) => button.classList.toggle("active", button.dataset.mode === state.mode));
}
function quickState(data) {
  const committed = data.domainState || data.transaction;
  const owner = data.visual.branch === "outbox" ? "Outbox" : data.visual.branch === "kafka" ? "Kafka Consumer" : data.visual.branch === "redis" ? "Redis best-effort (retry 없음)" : null;
  const rows = [["COMMITTED", committed], ["OUTBOX", data.outbox], ["KAFKA", data.kafka], ["RETRY OWNER", owner]].filter(([, value]) => value != null);
  return rows.length ? `<dl class="quick-state">${rows.map(([key, value]) => `<div><dt>${key}</dt><dd>${value}</dd></div>`).join("")}</dl>` : "";
}
function stop() { clearInterval(state.timer); state.timer = null; }
function resetStep() { stop(); state.step = 0; render(); }
function advance() { if (state.step >= currentScenario().steps.length - 1) { stop(); state.step = 0; render(); return; } state.step++; render(); }
$("chapterSelect").onchange = (event) => { state.chapter = Number(event.target.value); state.scenario = 0; resetStep(); }; $("scenarioSelect").onchange = (event) => { state.scenario = Number(event.target.value); resetStep(); };
$("play").onclick = () => { if (state.step === currentScenario().steps.length - 1) state.step = 0; stop(); render(); state.timer = setInterval(advance, Number($("speed").value)); }; $("pause").onclick = stop; $("next").onclick = advance; $("prev").onclick = () => { stop(); state.step = Math.max(0, state.step - 1); render(); }; $("reset").onclick = resetStep; $("speed").onchange = () => { if (state.timer) $("play").click(); };
document.querySelectorAll("[data-mode]").forEach((button) => button.onclick = () => { state.mode = button.dataset.mode; render(); }); render();
