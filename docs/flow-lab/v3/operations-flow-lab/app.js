/* Playback and rendering only. Every visual decision comes from step.visual. */
const $ = (id) => document.getElementById(id);
const state = { chapter: 0, scenario: 0, step: 0, mode: "presentation", timer: null };
const currentChapter = () => chapters[state.chapter];
const currentScenario = () => currentChapter().scenarios[state.scenario];
const currentStep = () => currentScenario().steps[state.step];
const statusClass = (value) => ({ [FACT.VERIFIED]: "verified", [FACT.MEASURED]: "measured", [FACT.DESIGN]: "design", [FACT.REJECTED]: "decision", [FACT.FUTURE]: "future", [FACT.MERGED]: "merged" }[value]);
const format = (value) => value == null ? "not applicable" : value;
/* Ch6는 서버 topology 대신 moderationTopology(판정 경로)를 쓴다. 두 topology 모두 같은
   canvas-node/connector/token 렌더링을 그대로 재사용한다 — 별도 renderer를 새로 만들지 않는다. */
const topologyFor = (data) => data.topologyKey === "moderation" ? moderationTopology : topology;
function populateSelects() {
  $("chapterSelect").innerHTML = chapters.map((item, i) => `<option value="${i}">${item.shortLabel}</option>`).join("");
  $("chapterSelect").value = state.chapter;
  $("scenarioSelect").innerHTML = currentChapter().scenarios.map((item, i) => `<option value="${i}">${item.title}</option>`).join("");
  $("scenarioSelect").value = state.scenario;
}
function renderCanvas(data) {
  const v = data.visual;
  const t = topologyFor(data);
  const edgeSvg = Object.entries(t.edges).map(([id, path]) => `<path id="edge-${id}" class="connector ${v.activeEdges.includes(id) ? "active" : "dim"}" d="${path}"/>`).join("");
  const tokenSvg = v.activeEdges.map((id) => tokenSvgFor(t, id, v.token)).join("");
  const labelSvg = v.activeEdges.map((id) => edgeLabelSvg(t, id, v.token)).join("");
  const nodesSvg = t.nodes.map(([id, label]) => {
    const [x, y] = t.nodePositions[id];
    const active = v.activeNodes.includes(id);
    const committed = !active && v.committedNodes.includes(id);
    const cls = active ? "active" : committed ? "committed" : "dim";
    const text = committed ? `${label} ✓` : label;
    return `<g class="canvas-node ${cls}" transform="translate(${x} ${y})"><rect width="100" height="70" rx="10"/><text x="50" y="42">${text}</text></g>`;
  }).join("");
  const badgeSvg = badgeSvgFor(t, v.badge);
  $("canvas").innerHTML = `<svg class="topology" viewBox="${t.viewBox}" role="img" aria-label="현재 Chapter의 고정 흐름 topology — 활성 path 위의 token만 이동한다"><g class="connectors">${edgeSvg}</g><g class="edge-labels">${labelSvg}</g><g class="tokens">${tokenSvg}</g><g class="nodes">${nodesSvg}</g>${badgeSvg}</svg><div class="flow-outcome ${v.outcome || ""}">${v.outcome ? `${outcomeLabel(v.outcome)} · ${data.action}` : ""}</div>`;
}
function badgeSvgFor(t, badge) {
  if (!badge) return "";
  const [x, y] = t.nodePositions[badge.nodeId];
  const width = badge.text.length * 6.5 + 16;
  return `<g class="node-badge" transform="translate(${x + 50} ${y - 14})"><rect x="${-width / 2}" y="-11" width="${width}" height="20" rx="6"/><text x="0" y="4">${badge.text}</text></g>`;
}
function tokenSvgFor(t, edgeId, kind) {
  const shape = kind === "event" ? "<rect x=\"-5\" y=\"-5\" width=\"10\" height=\"10\" transform=\"rotate(45)\"/>" : kind === "broadcast" ? "<path d=\"M-6 -5 L6 0 L-6 5 Z\"/>" : kind === "retry" ? "<text y=\"5\">↻</text>" : kind === "failure" ? "<text y=\"5\">×</text>" : kind === "dlt" ? "<text y=\"5\">↓</text>" : "<circle r=\"5\"/>";
  return `<g class="token ${kind || "event"}">${shape}<animateMotion dur="1.25s" repeatCount="indefinite" path="${t.edges[edgeId]}"/></g>`;
}
function edgeLabelSvg(t, edgeId, kind) {
  const position = t.labels[edgeId]; if (!position || !kind) return "";
  const [x, y] = position; return `<g class="edge-label" transform="translate(${x} ${y})"><rect x="-4" y="-12" width="${tokenLabel(kind).length * 6 + 10}" height="18" rx="5"/><text x="1" y="1">${tokenLabel(kind)}</text></g>`;
}
function tokenLabel(token) { return ({ request: "● request", event: "◆ event", commit: "✓ commit", retry: "↻ retry", failure: "× failure", dlt: "↓ DLT", broadcast: "↠ broadcast" })[token] || ""; }
function outcomeLabel(outcome) { return ({ committed: "✓ committed", acknowledged: "✓ broker ACK", completed: "✓ completed", delivered: "↠ delivered", failure: "× failure", dlt: "↓ DLT", skipped: "⏭ skipped", "not verified": "? not verified" })[outcome] || outcome; }
function laneNodeSvg(cx, states, labels) {
  const r = 10;
  const lines = states.slice(0, -1).map((state, i) => {
    const blocked = states[i + 1] === "blocked";
    return `<line x1="${cx[i]}" y1="30" x2="${cx[i + 1]}" y2="30" class="lane-line ${blocked ? "blocked" : state === "pending" ? "pending" : "done"}"/>`;
  }).join("");
  const nodes = states.map((state, i) => `<g class="lane-node ${state}"><circle cx="${cx[i]}" cy="30" r="${r}"/>${state === "done" ? `<text x="${cx[i]}" y="34">✓</text>` : state === "blocked" ? `<text x="${cx[i]}" y="34">×</text>` : ""}</g>`).join("");
  const labelSvg = labels.map((label, i) => `<text x="${cx[i]}" y="56" class="lane-label">${label}</text>`).join("");
  return `${lines}${nodes}${labelSvg}`;
}
function renderComparison(data) {
  const element = $("comparison"); element.hidden = !currentScenario().comparison; if (element.hidden) return;
  const lanes = data.comparison;
  const cx = [45, 130, 215, 300];
  const stageLabels = currentChapter().stageLabels || [];
  element.innerHTML = `<article class="lane"><span class="badge verified">V2 BEFORE — baseline Evidence</span><h2>AFTER_COMMIT</h2>
    <svg class="lane-strip" viewBox="0 0 345 62">${laneNodeSvg(cx, lanes.v2States, stageLabels)}</svg><p>${lanes.v2}</p></article>
    <article class="lane"><span class="badge verified">V3 AFTER — Transactional Outbox</span><h2>Outbox</h2>
    <svg class="lane-strip" viewBox="0 0 345 62">${laneNodeSvg(cx, lanes.v3States, stageLabels)}</svg><p>${lanes.v3}</p></article>`;
}
function renderPerformance(data) {
  const element = $("performance"); element.hidden = !data.performance; if (element.hidden) { element.innerHTML = ""; return; }
  element.innerHTML = data.performance.map((row) => {
    if (row.after == null) return `<article class="perf-stat"><h3>${row.metric}</h3><p class="perf-value">${row.before}</p></article>`;
    /* beforeValue/afterValue는 display 문자열과 별개인 같은-unit(scaleUnit) 계산값이다.
       display 문자열의 단위가 서로 다를 수 있어(예: "1.706s" vs "265.54ms") 문자열에서
       숫자만 뽑아 비교하면 방향이 뒤집힐 수 있다 — 명시 값이 없을 때만 문자열 파싱으로 대체한다. */
    const beforeNum = row.beforeValue != null ? row.beforeValue : parseFloat(String(row.before).replace(/[^0-9.]/g, "")) || 1;
    const afterNum = row.afterValue != null ? row.afterValue : parseFloat(String(row.after).replace(/[^0-9.]/g, "")) || 0;
    const max = Math.max(beforeNum, afterNum, 1);
    return `<article class="perf-compare"><h3>${row.metric}</h3>
      <div class="perf-bar-row"><span class="perf-bar-label">Before</span><div class="perf-bar"><div class="perf-bar-fill before" style="width:${(beforeNum / max) * 100}%"></div></div><span class="perf-bar-value">${row.before}</span></div>
      <div class="perf-bar-row"><span class="perf-bar-label">After</span><div class="perf-bar"><div class="perf-bar-fill after" style="width:${(afterNum / max) * 100}%"></div></div><span class="perf-bar-value">${row.after}</span></div>
      ${row.improvement ? `<p class="perf-improvement">${row.improvement}</p>` : ""}</article>`;
  }).join("");
}
/* Kafka Partition 분포. #performance와 같은 perf-stat/perf-bar 구조를 재사용한다 — 별도 CSS 없음. */
function renderKafkaPartitions(data) {
  const element = $("kafkaPartitions"); element.hidden = !data.kafkaPartitions; if (element.hidden) { element.innerHTML = ""; return; }
  const max = Math.max(...data.kafkaPartitions.map((partition) => partition.count), 1);
  element.innerHTML = data.kafkaPartitions.map((partition) => `<article class="perf-compare"><h3>${partition.id}</h3>
    <div class="perf-bar-row"><span class="perf-bar-label">건수</span><div class="perf-bar"><div class="perf-bar-fill ${partition.count > 0 ? "after" : "before"}" style="width:${(partition.count / max) * 100}%"></div></div><span class="perf-bar-value">${partition.count}건</span></div>
    </article>`).join("");
}
function linked(refs) { return refs.length ? refs.map((item) => `<a href="${item.href}" target="_blank" rel="noreferrer">${item.label}</a>`).join("<br>") : "not applicable"; }
function renderDetails(data) {
  const entries = [["Why", data.narration], ["Code", data.codeReferences.length ? data.codeReferences.join(" · ") : "not applicable"],
    ["Runtime", "코드·Evidence 기반 정적 시뮬레이션 — 라이브 JVM이 아님"],
    ["Transaction / Lock", `Transaction: ${format(data.transaction)}<br>Lock: ${format(data.lock)}`],
    ["Event / Infra", `Outbox: ${format(data.outbox)}<br>Kafka: ${format(data.kafka)}<br>Consumer: ${format(data.consumer)}<br>Redis: ${format(data.redis)}`],
    ["Logs / Metrics", `Logs: ${format(data.logs)}<br>Metrics: ${format(data.metrics)}`], ["Evidence", linked(data.evidenceReferences)], ["Limits", format(data.limits)]];
  if (data.fullPrompt) entries.push(["Prompt 원문", data.fullPrompt]);
  if (data.sideNote) entries.push([data.sideNote.title, data.sideNote.body]);
  $("detailGrid").innerHTML = entries.map(([title, value]) => `<article><h3>${title}</h3><p>${value}</p></article>`).join("");
}
function renderOps(data) { $("opsGrid").innerHTML = [["Structured log", data.logs], ["Metric", data.metrics], ["Evidence", linked(data.evidenceReferences)], ["Limit", data.limits]].map(([title, value]) => `<article class="${value == null ? "na" : ""}"><h3>${title}</h3><p>${format(value)}</p></article>`).join(""); }
function formatModerationResult(result) {
  return `provider=${result.provider} · model=${result.model}<br>promptVersion=${result.promptVersion} · policyVersion=${result.policyVersion}<br>result=${result.result} · categories=${result.categories} · riskLevel=${result.riskLevel}<br>tokens=${result.tokens}`;
}
function render() {
  const data = currentStep();
  document.body.dataset.mode = state.mode;
  populateSelects();
  $("chapterQuestion").textContent = currentChapter().title;
  $("chapterSubtitle").textContent = currentChapter().subtitle;
  renderCanvas(data); renderComparison(data); renderPerformance(data); renderKafkaPartitions(data); renderDetails(data); renderOps(data);
  $("stepTitle").textContent = data.action; $("narration").textContent = data.narration; $("counter").textContent = `Step ${state.step + 1} / ${currentScenario().steps.length}`;
  $("factBadge").textContent = data.factStatus; $("factBadge").className = `badge ${statusClass(data.factStatus)}`;
  $("stepFact").innerHTML = `<span class="badge ${statusClass(data.factStatus)}">${data.factStatus}</span>${data.decisionBadge ? `<span class="badge decision">${data.decisionBadge}</span>` : ""}${quickState(data)}${promptBlockSvg(data)}<p>${data.limits || "Evidence 범위 안에서 표시"}</p>`;
  const cards = [["Domain", data.domainState], ["Transaction", data.transaction], ["Outbox", data.outbox],
    ["Kafka / Consumer", [data.kafka, data.consumer].filter(Boolean).join(" / ") || null], ["Redis", data.redis], ["Outcome", data.visual.outcome]];
  if (data.moderationResult) cards.push(["ChatModeration DB", formatModerationResult(data.moderationResult)]);
  $("stateGrid").innerHTML = cards.map(([title, value]) => `<article class="${value == null ? "na" : ""}"><h3>${title}</h3><p>${format(value)}</p></article>`).join("");
  $("learningPanel").hidden = state.mode !== "learning"; $("opsPanel").hidden = state.mode !== "ops";
  $("evidenceGate").hidden = state.mode !== "learning";
  document.querySelectorAll("[data-mode]").forEach((button) => button.classList.toggle("active", button.dataset.mode === state.mode));
}
function quickState(data) {
  const committed = data.domainState || data.transaction;
  const rows = [["COMMITTED", committed], ["OUTBOX", data.outbox], ["KAFKA", data.kafka], ["RETRY OWNER", data.retryOwner]].filter(([, value]) => value != null);
  return rows.length ? `<dl class="quick-state">${rows.map(([key, value]) => `<div><dt>${key}</dt><dd>${value}</dd></div>`).join("")}</dl>` : "";
}
/* Prompt 원문 전체는 학습 상세(fullPrompt)에서만 펼친다 — 여기서는 정책 구성 블록만 pill로 보여준다. */
function promptBlockSvg(data) {
  if (!data.promptBlocks) return "";
  return `<div class="prompt-blocks">${data.promptBlocks.map((block) => `<span class="badge">${block}</span>`).join("")}</div>`;
}
function stop() { clearInterval(state.timer); state.timer = null; }
function resetStep() { stop(); state.step = 0; render(); }
function advance() { if (state.step >= currentScenario().steps.length - 1) { stop(); state.step = 0; render(); return; } state.step++; render(); }
$("chapterSelect").onchange = (event) => { state.chapter = Number(event.target.value); state.scenario = 0; resetStep(); }; $("scenarioSelect").onchange = (event) => { state.scenario = Number(event.target.value); resetStep(); };
$("play").onclick = () => { if (state.step === currentScenario().steps.length - 1) state.step = 0; stop(); render(); state.timer = setInterval(advance, Number($("speed").value)); }; $("pause").onclick = stop; $("next").onclick = advance; $("prev").onclick = () => { stop(); state.step = Math.max(0, state.step - 1); render(); }; $("reset").onclick = resetStep; $("speed").onchange = () => { if (state.timer) $("play").click(); };
document.querySelectorAll("[data-mode]").forEach((button) => button.onclick = () => { state.mode = button.dataset.mode; render(); }); render();
