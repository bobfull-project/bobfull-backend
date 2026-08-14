/* Playback and rendering only. Every visual decision comes from step.visual. */
const $ = (id) => document.getElementById(id);
const state = { chapter: 0, scenario: 0, step: 0, timer: null };
const currentChapter = () => chapters[state.chapter];
const currentScenario = () => currentChapter().scenarios[state.scenario];
const currentStep = () => currentScenario().steps[state.step];
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
    return `<g class="canvas-node ${cls}" transform="translate(${x} ${y})"><rect width="100" height="70" rx="6"/><text x="50" y="42">${text}</text></g>`;
  }).join("");
  const badgeSvg = badgeSvgFor(t, v.badge);
  $("canvas").innerHTML = `<svg class="topology" viewBox="${t.viewBox}" role="img" aria-label="현재 Chapter의 고정 흐름 topology — 활성 path 위의 token만 이동한다"><g class="connectors">${edgeSvg}</g><g class="edge-labels">${labelSvg}</g><g class="tokens">${tokenSvg}</g><g class="nodes">${nodesSvg}</g>${badgeSvg}</svg><div class="flow-outcome ${v.outcome || ""}">${v.outcome ? `${outcomeLabel(v.outcome)} · ${data.action}` : ""}</div>`;
}
function badgeSvgFor(t, badge) {
  if (!badge) return "";
  const [x, y] = t.nodePositions[badge.nodeId];
  const width = badge.text.length * 6.5 + 16;
  return `<g class="node-badge" transform="translate(${x + 50} ${y - 14})"><rect x="${-width / 2}" y="-11" width="${width}" height="20" rx="4"/><text x="0" y="4">${badge.text}</text></g>`;
}
function tokenSvgFor(t, edgeId, kind) {
  const shape = kind === "event" ? "<rect x=\"-5\" y=\"-5\" width=\"10\" height=\"10\" transform=\"rotate(45)\"/>" : kind === "broadcast" ? "<path d=\"M-6 -5 L6 0 L-6 5 Z\"/>" : kind === "retry" ? "<text y=\"5\">↻</text>" : kind === "failure" ? "<text y=\"5\">×</text>" : kind === "dlt" ? "<text y=\"5\">↓</text>" : "<circle r=\"5\"/>";
  return `<g class="token ${kind || "event"}">${shape}<animateMotion dur="1.25s" repeatCount="indefinite" path="${t.edges[edgeId]}"/></g>`;
}
function edgeLabelSvg(t, edgeId, kind) {
  const position = t.labels[edgeId]; if (!position || !kind) return "";
  const [x, y] = position; return `<g class="edge-label" transform="translate(${x} ${y})"><rect x="-4" y="-12" width="${tokenLabel(kind).length * 6 + 10}" height="18" rx="3"/><text x="1" y="1">${tokenLabel(kind)}</text></g>`;
}
function tokenLabel(token) { return ({ request: "● 요청", event: "◆ 이벤트", commit: "✓ 확정", retry: "↻ 재시도", failure: "× 실패", dlt: "↓ DLT", broadcast: "↠ 전파" })[token] || ""; }
function outcomeLabel(outcome) { return ({ committed: "✓ 확정됨", acknowledged: "✓ 브로커 ACK", completed: "✓ 완료", delivered: "↠ 전달됨", failure: "× 실패", dlt: "↓ DLT 이동", skipped: "⏭ 건너뜀", "not verified": "? 미검증" })[outcome] || outcome; }
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
  element.innerHTML = `<article class="lane"><span class="lane-tag">V2 이전 방식 — 실패 사례</span><h3>확정 후 메모리 처리(AFTER_COMMIT)</h3>
    <svg class="lane-strip" viewBox="0 0 345 62">${laneNodeSvg(cx, lanes.v2States, stageLabels)}</svg><p>${lanes.v2}</p></article>
    <article class="lane"><span class="lane-tag">V3 현재 방식 — 안전 장치 추가</span><h3>발행 대기함 패턴(Outbox)</h3>
    <svg class="lane-strip" viewBox="0 0 345 62">${laneNodeSvg(cx, lanes.v3States, stageLabels)}</svg><p>${lanes.v3}</p></article>`;
}
/* beforeValue/afterValue는 display 문자열과 별개인 같은-unit(scaleUnit) 계산값이다.
   display 문자열의 단위가 서로 다를 수 있어(예: "1.706s" vs "265.54ms") 문자열에서
   숫자만 뽑아 비교하면 방향이 뒤집힐 수 있다 — 명시 값이 없을 때만 문자열 파싱으로 대체한다.
   Step 패널과 하단 성능 개선 요약이 이 렌더링을 그대로 공유한다. */
function performanceRowHtml(row) {
  if (row.after == null) return `<article class="perf-stat"><h3>${row.metric}</h3><p class="perf-value">${row.before}</p></article>`;
  const beforeNum = row.beforeValue != null ? row.beforeValue : parseFloat(String(row.before).replace(/[^0-9.]/g, "")) || 1;
  const afterNum = row.afterValue != null ? row.afterValue : parseFloat(String(row.after).replace(/[^0-9.]/g, "")) || 0;
  const max = Math.max(beforeNum, afterNum, 1);
  return `<article class="perf-compare"><h3>${row.metric}</h3>
    <div class="perf-bar-row"><span class="perf-bar-label">Before</span><div class="perf-bar"><div class="perf-bar-fill before" style="width:${(beforeNum / max) * 100}%"></div></div><span class="perf-bar-value">${row.before}</span></div>
    <div class="perf-bar-row"><span class="perf-bar-label">After</span><div class="perf-bar"><div class="perf-bar-fill after" style="width:${(afterNum / max) * 100}%"></div></div><span class="perf-bar-value">${row.after}</span></div>
    ${row.improvement ? `<p class="perf-improvement">${row.improvement}</p>` : ""}</article>`;
}
function renderPerformance(data) {
  const element = $("performance"); element.hidden = !data.performance; if (element.hidden) { element.innerHTML = ""; return; }
  element.innerHTML = data.performance.map(performanceRowHtml).join("");
}
/* Kafka Partition 분포. #performance와 같은 perf-stat/perf-bar 구조를 재사용한다 — 별도 CSS 없음. */
function renderKafkaPartitions(data) {
  const element = $("kafkaPartitions"); element.hidden = !data.kafkaPartitions; if (element.hidden) { element.innerHTML = ""; return; }
  const max = Math.max(...data.kafkaPartitions.map((partition) => partition.count), 1);
  element.innerHTML = data.kafkaPartitions.map((partition) => `<article class="perf-compare"><h3>${partition.id}</h3>
    <div class="perf-bar-row"><span class="perf-bar-label">건수</span><div class="perf-bar"><div class="perf-bar-fill ${partition.count > 0 ? "after" : "before"}" style="width:${(partition.count / max) * 100}%"></div></div><span class="perf-bar-value">${partition.count}건</span></div>
    </article>`).join("");
}
function linked(refs) { return refs.map((item) => `<a href="${item.href}" target="_blank" rel="noreferrer">${item.label}</a>`).join("<br>"); }
/* narration이 이미 "왜"를 설명하므로 여기서는 반복하지 않는다. Transaction/Lock/Event/Infra/Logs/Metrics는
   아래 "핵심 상태"에서 이미 보여주므로 여기서 중복하지 않는다. Code는 "코드로 보기"에서 실제 코드로 보여준다.
   값이 없는 항목은 "not applicable"로 채우지 않고 아예 표시하지 않는다. */
function renderDetails(data) {
  const entries = [];
  if (data.limits) entries.push(["한계", data.limits]);
  if (data.evidenceReferences.length) entries.push(["근거", linked(data.evidenceReferences)]);
  if (data.fullPrompt) entries.push(["프롬프트 원문", data.fullPrompt]);
  if (data.sideNote) entries.push([data.sideNote.title, data.sideNote.body]);
  const el = $("detailGrid");
  el.hidden = entries.length === 0;
  el.innerHTML = entries.map(([title, value]) => `<article><h3>${title}</h3><p>${value}</p></article>`).join("");
}
function escapeHtml(text) { return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }
/* 현재 Step에 실제 코드 발췌(codeSnippet)가 있으면 그대로 보여준다 — 없으면 codeReferences만 안내한다. */
function renderCodeView(data) {
  const el = $("codeCard");
  if (data.codeSnippet) {
    el.innerHTML = `<div class="code-card-head"><span class="code-file">${data.codeSnippet.file}</span></div><pre class="code-block"><code>${escapeHtml(data.codeSnippet.code)}</code></pre>`;
    return;
  }
  el.innerHTML = `<p class="code-empty">이 Step에는 별도로 발췌한 코드가 없습니다.${data.codeReferences.length ? ` 참고: ${data.codeReferences.join(" · ")}` : ""}</p>`;
}
function formatModerationResult(result) {
  return `provider=${result.provider} · model=${result.model}<br>promptVersion=${result.promptVersion} · policyVersion=${result.policyVersion}<br>result=${result.result} · categories=${result.categories} · riskLevel=${result.riskLevel}<br>tokens=${result.tokens}`;
}
/* 화면에 항상 보이는 최소 caption이다. REJECTED/FUTURE만 강조하고
   나머지 factStatus는 evidence 1건과 함께 조용한 caption으로 보여준다. */
function factCaption(data) {
  const primary = data.evidenceReferences.length ? data.evidenceReferences[0].label.split(" ")[0] : null;
  const label = primary ? `${data.factStatus} · ${primary}` : data.factStatus;
  const strong = data.factStatus === FACT.REJECTED || data.factStatus === FACT.FUTURE;
  return `<span class="${strong ? "fact-strong" : ""}">${label}</span>${data.decisionBadge ? ` — <span class="decision">${data.decisionBadge}</span>` : ""}`;
}
function render() {
  const data = currentStep();
  populateSelects();
  $("chapterQuestion").textContent = currentChapter().title;
  $("chapterSubtitle").textContent = currentChapter().subtitle;
  renderCanvas(data); renderComparison(data); renderPerformance(data); renderKafkaPartitions(data); renderDetails(data); renderCodeView(data);
  $("stepTitle").textContent = data.action; $("narration").textContent = data.narration; $("counter").textContent = `Step ${state.step + 1} / ${currentScenario().steps.length}`;
  $("stepFact").innerHTML = `<p class="fact-caption">${factCaption(data)}</p>${quickState(data)}${promptBlockText(data)}`;
  const cards = [["Domain", data.domainState], ["Transaction", data.transaction], ["Outbox", data.outbox],
    ["Kafka / Consumer", [data.kafka, data.consumer].filter(Boolean).join(" / ") || null], ["Redis", data.redis], ["Outcome", data.visual.outcome]];
  if (data.moderationResult) cards.push(["ChatModeration DB", formatModerationResult(data.moderationResult)]);
  $("stateGrid").innerHTML = cards.map(([title, value]) => `<article class="${value == null ? "na" : ""}"><h3>${title}</h3><p>${format(value)}</p></article>`).join("");
  renderPerformanceHighlights();
  renderDecisionHighlights();
}
function quickState(data) {
  const committed = data.domainState || data.transaction;
  const rows = [["확정 상태", committed], ["Outbox", data.outbox], ["Kafka", data.kafka], ["재시도 담당", data.retryOwner]].filter(([, value]) => value != null);
  return rows.length ? `<dl class="quick-state">${rows.map(([key, value]) => `<div><dt>${key}</dt><dd>${value}</dd></div>`).join("")}</dl>` : "";
}
/* Prompt 원문 전체는 상세 패널의 fullPrompt에서만 펼친다 — 여기서는 정책 구성 블록을 조용한 caption 한 줄로 보여준다. */
function promptBlockText(data) {
  if (!data.promptBlocks) return "";
  return `<p class="prompt-blocks">${data.promptBlocks.join(" · ")}</p>`;
}
/* 페이지 하단 "성능 개선 / 신뢰성·설계 개선" 요약은 현재 선택된 Chapter로 필터링해서 보여준다 —
   그 Chapter에 해당 성과가 없으면 섹션 자체를 숨긴다. scenario-data.js에 이미 있는 실제 step을
   그대로 다시 보여줄 뿐, 새 사실을 추가하지 않는다. */
function findStep(chapterId, scenarioId, stepId) {
  const chapter = chapters.find((item) => item.id === chapterId);
  const scenario = chapter.scenarios.find((item) => item.id === scenarioId);
  return scenario.steps.find((item) => item.id === stepId);
}
/* whatStep: 무엇을 바꿨는지 설명하는 narration을 가진 step. measureSteps: 그 변경으로 나온 실제 수치. */
const PERFORMANCE_HIGHLIGHTS = [
  { chapter: "hotpath-performance", scenario: "batch-optimization", whatStep: "batch-fix", measureSteps: ["same-load-result", "stress-result"] },
  { chapter: "kafka-mechanics", scenario: "kafka-adoption-decision", whatStep: "after-message-id-key", measureSteps: ["after-message-id-key"] }
];
function renderPerformanceHighlights() {
  const cards = PERFORMANCE_HIGHLIGHTS.filter((card) => card.chapter === currentChapter().id);
  $("performanceOutcome").hidden = cards.length === 0;
  $("performanceHighlights").innerHTML = cards.map((card) => {
    const whatStep = findStep(card.chapter, card.scenario, card.whatStep);
    const rows = card.measureSteps.flatMap((id) => findStep(card.chapter, card.scenario, id).performance.filter((row) => row.improvement));
    return `<article class="perf-highlight"><h3>${whatStep.action}</h3><p>${whatStep.narration}</p><div class="performance">${rows.map(performanceRowHtml).join("")}</div></article>`;
  }).join("");
}
const DECISION_HIGHLIGHTS = [
  { chapter: "outbox", scenario: "chatroom-outbox", step: "retry" },
  { chapter: "kafka-ai", scenario: "publish-failure", step: "retry" },
  { chapter: "kafka-ai", scenario: "retry-exhausted-dlt", step: "dlt" },
  { chapter: "redis", scenario: "aws-cross-instance-normal", step: "cross-instance" },
  { chapter: "kafka-mechanics", scenario: "kafka-adoption-decision", step: "reliability" },
  { chapter: "ai-moderation", scenario: "clear-flagged-fast-path", step: "persisted" },
  { chapter: "ai-moderation", scenario: "why-not-context-llm", step: "rejected-decision" }
];
function renderDecisionHighlights() {
  const cards = DECISION_HIGHLIGHTS.filter((ref) => ref.chapter === currentChapter().id);
  $("reliabilityOutcome").hidden = cards.length === 0;
  $("reliabilityHighlights").innerHTML = cards.map((ref) => {
    const step = findStep(ref.chapter, ref.scenario, ref.step);
    const note = step.sideNote ? `<p class="side-note">${step.sideNote.body}</p>` : "";
    return `<article><h3>${step.action}</h3><p>${step.narration}</p>${step.decisionBadge ? `<p class="decision">${step.decisionBadge}</p>` : ""}${note}</article>`;
  }).join("");
  $("outcomes").hidden = $("performanceOutcome").hidden && $("reliabilityOutcome").hidden;
}
function stop() { clearInterval(state.timer); state.timer = null; }
function resetStep() { stop(); state.step = 0; render(); }
function advance() { if (state.step >= currentScenario().steps.length - 1) { stop(); state.step = 0; render(); return; } state.step++; render(); }
$("chapterSelect").onchange = (event) => { state.chapter = Number(event.target.value); state.scenario = 0; resetStep(); }; $("scenarioSelect").onchange = (event) => { state.scenario = Number(event.target.value); resetStep(); };
$("play").onclick = () => { if (state.step === currentScenario().steps.length - 1) state.step = 0; stop(); render(); state.timer = setInterval(advance, Number($("speed").value)); }; $("pause").onclick = stop; $("next").onclick = advance; $("prev").onclick = () => { stop(); state.step = Math.max(0, state.step - 1); render(); }; $("reset").onclick = resetStep; $("speed").onchange = () => { if (state.timer) $("play").click(); };
render();
