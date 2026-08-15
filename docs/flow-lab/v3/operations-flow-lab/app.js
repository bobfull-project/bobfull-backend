/* Playback and rendering only. Every visual decision comes from step.visual. */
const $ = (id) => document.getElementById(id);
const state = { chapter: 0, scenario: 0, step: 0, timer: null };
const currentChapter = () => chapters[state.chapter];
const currentScenario = () => currentChapter().scenarios[state.scenario];
const currentStep = () => currentScenario().steps[state.step];
/* Ch6는 서버 topology 대신 moderationTopology(판정 경로)를 쓴다. 두 topology 모두 같은
   canvas-node/connector/token 렌더링을 그대로 재사용한다 — 별도 renderer를 새로 만들지 않는다. */
const topologyFor = (data) => data.topologyKey === "moderation" ? moderationTopology : topology;
function populateSelects() {
  $("chapterSelect").innerHTML = chapters.map((item, i) => `<option value="${i}">${item.shortLabel}</option>`).join("");
  $("chapterSelect").value = state.chapter;
}
function renderScenarioButtons() {
  $("scenarioButtons").innerHTML = currentChapter().scenarios.map((item, i) =>
    `<button type="button" class="${i === state.scenario ? "active" : ""}" data-scenario="${i}">${item.title}</button>`).join("");
}
function syncPlaybackPadding() {
  document.querySelector("main").style.paddingBottom = `${$("play").closest(".playback").offsetHeight + 24}px`;
}
/* sticky 도표가 topbar 바로 아래에 붙도록, topbar 실제 높이를 CSS 변수로 맞춘다(반응형 줄바꿈 대비). */
function syncTopbarHeightVar() {
  document.documentElement.style.setProperty("--topbar-h", `${document.querySelector(".topbar").offsetHeight}px`);
}
/* "크게 보기" — 같은 #canvas/#flowCaption 엘리먼트를 그대로 옮긴다. 다시 렌더링하지 않으므로
   확장/축소 전후로 강조 상태·시나리오 진행이 그대로 유지된다. */
function expandCanvasView() {
  $("canvasOverlayBody").append($("canvas"), $("flowCaption"));
  $("canvasOverlay").hidden = false;
  document.body.style.overflow = "hidden";
}
function collapseCanvasView() {
  $("canvasSlot").appendChild($("canvas"));
  $("stageSticky").appendChild($("flowCaption"));
  $("canvasOverlay").hidden = true;
  document.body.style.overflow = "";
}
function renderCanvas(data) {
  const v = data.visual;
  const t = topologyFor(data);
  const edgeSvg = Object.entries(t.edges).map(([id, path]) => `<path id="edge-${id}" class="connector ${v.activeEdges.includes(id) ? "active" : "dim"}" d="${path}"/>`).join("");
  const tokenSvg = v.activeEdges.map((id) => tokenSvgFor(t, id, v.token)).join("");
  const labelSvg = v.activeEdges.map((id) => edgeLabelSvg(t, id, v.token, v.edgeLabels && v.edgeLabels[id])).join("");
  const nodesSvg = t.nodes.map(([id, label]) => {
    const [x, y] = t.nodePositions[id];
    const active = v.activeNodes.includes(id);
    const committed = !active && v.committedNodes.includes(id);
    const cls = active ? "active" : committed ? "committed" : "dim";
    const text = committed ? `${label} ✓` : label;
    /* 박스(100 너비, 좌우 여백 감안 84)보다 긴 라벨은 textLength로 압축해 테두리 밖으로 넘치지 않게 한다. */
    const compress = text.length > 9 ? ` textLength="84" lengthAdjust="spacingAndGlyphs"` : "";
    return `<g class="canvas-node ${cls}" transform="translate(${x} ${y})"><rect width="100" height="70" rx="6"/><text x="50" y="42"${compress}>${text}</text></g>`;
  }).join("");
  const badgeSvg = badgeSvgFor(t, v.badge);
  $("canvas").innerHTML = `<svg class="topology" viewBox="${t.viewBox}" role="img" aria-label="현재 Chapter의 고정 흐름 topology — 활성 path 위의 token만 이동한다"><g class="connectors">${edgeSvg}</g><g class="edge-labels">${labelSvg}</g><g class="tokens">${tokenSvg}</g><g class="nodes">${nodesSvg}</g>${badgeSvg}</svg><div class="flow-outcome ${v.outcome || ""}">${v.outcome ? `${outcomeLabel(v.outcome)} · ${data.action}` : ""}</div>`;
  $("flowCaption").textContent = data.action.replace(/^[✓×◆●↻↓↠▲?]\s*/, "");
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
function edgeLabelSvg(t, edgeId, kind, override) {
  const position = t.labels[edgeId]; if (!position) return "";
  const label = override || tokenLabel(kind); if (!label) return "";
  const [x, y] = position; return `<g class="edge-label" transform="translate(${x} ${y})"><rect x="-4" y="-12" width="${label.length * 7.5 + 10}" height="18" rx="3"/><text x="1" y="1">${label}</text></g>`;
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
/* p95·RPS·dropped_iterations처럼 이름만으로는 뜻을 알 수 없는 지표를 같은 화면에서 바로 풀어준다. */
function metricGlossaryHtml(rows) {
  if (!rows) return "";
  return `<dl class="metric-glossary">${rows.map(([term, desc]) => `<div><dt>${term}</dt><dd>${desc}</dd></div>`).join("")}</dl>`;
}
function renderPerformance(data) {
  const element = $("performance"); element.hidden = !data.performance; if (element.hidden) { element.innerHTML = ""; return; }
  element.innerHTML = data.performance.map(performanceRowHtml).join("") + metricGlossaryHtml(data.metricGlossary);
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
  if (data.moderationResult) entries.push(["ChatModeration DB 저장값", formatModerationResult(data.moderationResult)]);
  if (data.limits) entries.push(["한계", data.limits]);
  if (data.evidenceReferences.length) entries.push(["근거", linked(data.evidenceReferences)]);
  if (data.fullPrompt) entries.push(["프롬프트 원문", data.fullPrompt]);
  if (data.sideNote) entries.push([data.sideNote.title, data.sideNote.body]);
  const el = $("detailGrid");
  el.hidden = entries.length === 0;
  el.innerHTML = entries.map(([title, value]) => `<article><h3>${title}</h3><p>${value}</p></article>`).join("");
}
function escapeHtml(text) { return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }
/* 디버거처럼 현재 Scenario의 전체 Step 제목을 번호와 함께 보여주고 현재 Step만 강조한다.
   action 앞의 상태 기호(✓ × ◆ ● 등)는 여기서는 번호가 이미 그 역할을 하므로 떼어낸다. */
function renderCodeStepper() {
  $("codeStepper").innerHTML = currentScenario().steps.map((item, i) =>
    `<span class="${i === state.step ? "active" : ""}">${i + 1}. ${item.action.replace(/^[✓×◆●↻↓↠▲?]\s*/, "")}</span>`).join("");
}
/* Java 최소 하이라이터. 주석/문자열을 먼저 통째로 잡아 그 안의 키워드가 다시 매칭되지 않게 한다. */
const JAVA_KEYWORDS = /\b(public|private|protected|static|final|class|interface|enum|record|new|return|if|else|for|while|try|catch|throw|throws|import|package|extends|implements|void|boolean|int|long|double|char|byte|short|this|null|true|false|instanceof|switch|case|break|continue|default)\b/;
function highlightJava(code) {
  const pattern = new RegExp([
    /\/\/[^\n]*/.source, /\/\*[\s\S]*?\*\//.source,
    /"(?:\\.|[^"\\])*"/.source, /@[A-Za-z_][A-Za-z0-9_]*/.source,
    JAVA_KEYWORDS.source, /\b[A-Z][A-Za-z0-9_]*\b/.source,
    /\b[a-zA-Z_][A-Za-z0-9_]*(?=\s*\()/.source, /\b\d[\d_.]*[LlFfDd]?\b/.source
  ].join("|"), "g");
  let out = "", last = 0, match;
  while ((match = pattern.exec(code)) !== null) {
    out += escapeHtml(code.slice(last, match.index));
    const token = match[0];
    const cls = token.startsWith("//") || token.startsWith("/*") ? "tok-comment"
      : token.startsWith("\"") ? "tok-string" : token.startsWith("@") ? "tok-anno"
      : new RegExp(`^(?:${JAVA_KEYWORDS.source})$`).test(token) ? "tok-keyword"
      : /^\d/.test(token) ? "tok-number" : /^[A-Z]/.test(token) ? "tok-type" : "tok-method";
    out += `<span class="${cls}">${escapeHtml(token)}</span>`;
    last = match.index + token.length;
  }
  return out + escapeHtml(code.slice(last));
}
/* annotations: [{from, to, text}] — code의 해당 줄 구간(1-based, to 포함)만 테두리로 묶고 설명을 붙인다.
   구간은 겹치지 않고 순서대로 온다고 가정한다(데이터 작성 시 보장). */
function highlightWithAnnotations(snippet) {
  const lines = snippet.code.split("\n");
  if (!snippet.annotations || !snippet.annotations.length) return highlightJava(snippet.code);
  /* 각 chunk는 연속된 줄 범위이므로 정확히 "\n" 하나로 이어붙여야 원문 줄바꿈이 보존된다. */
  const chunks = [];
  let cursor = 0;
  snippet.annotations.forEach((note, index) => {
    const from = note.from - 1, to = note.to;
    if (from > cursor) chunks.push(highlightJava(lines.slice(cursor, from).join("\n")));
    chunks.push(`<span class="code-annotated"><span class="code-annotation"><span class="num">${index + 1}</span>${escapeHtml(note.text)}</span>${highlightJava(lines.slice(from, to).join("\n"))}</span>`);
    cursor = to;
  });
  if (cursor < lines.length) chunks.push(highlightJava(lines.slice(cursor).join("\n")));
  return chunks.join("\n");
}
/* 현재 Step에 실제 코드 발췌(codeSnippet)가 있으면 그대로 보여준다 — 없으면 codeReferences만 안내한다. */
function renderCodeView(data) {
  const el = $("codeCard");
  if (data.codeSnippet) {
    const methodTag = data.codeSnippet.method
      ? `<span class="code-method-tag">실제 코드</span><span class="code-method">${data.codeSnippet.method}</span><br>` : "";
    el.innerHTML = `<div class="code-card-head">${methodTag}<span class="code-file">${data.codeSnippet.file}</span></div><pre class="code-block"><code>${highlightWithAnnotations(data.codeSnippet)}</code></pre>`;
    return;
  }
  el.innerHTML = `<p class="code-empty">이 Step에는 별도로 발췌한 코드가 없습니다.${data.codeReferences.length ? ` 참고: ${data.codeReferences.join(" · ")}` : ""}</p>`;
}
/* 실행 주체·주기·재시도 한도를 화면에 고정 노출한다 — "Outbox가 스스로 실행한다"는 오해를 막는다. */
function renderRetryPolicy(data) {
  const el = $("retryPolicy");
  el.hidden = !data.retryPolicy; if (el.hidden) { el.innerHTML = ""; return; }
  el.innerHTML = data.retryPolicy.map(([term, value]) => `<div><span class="rp-term">${term}</span><span class="rp-value">${value}</span></div>`).join("");
}
/* "실패했을 때 무엇이 어디에 남는가"를 표 하나로 보여준다 — V3는 DB row, V2는 남는 게 없다. */
function renderStoreCompare(data) {
  const el = $("storeCompare");
  el.hidden = !data.storeCompare; if (el.hidden) { el.innerHTML = ""; return; }
  const { columns, row, v2Note, v3Note } = data.storeCompare;
  el.innerHTML = `<article class="store-box gone"><span class="store-tag">V2 이전 방식 — 메모리 리스너</span><h3>DB에 남는 것이 없음</h3>
      <p class="store-empty">저장 테이블 없음 — 재시도할 근거가 사라진다</p><p class="store-note">${v2Note}</p></article>
    <article class="store-box kept"><span class="store-tag">V3 현재 방식 — outbox_event 테이블</span><h3>DB에 작업이 그대로 남음</h3>
      <div class="store-table-wrap"><table class="store-table"><thead><tr>${columns.map((name) => `<th>${name}</th>`).join("")}</tr></thead>
      <tbody><tr>${row.map((value) => `<td>${value}</td>`).join("")}</tr></tbody></table></div><p class="store-note">${v3Note}</p></article>`;
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
/* 사람이 이해할 상태를 narration 바로 아래, 기술 정보보다 먼저 보여준다. checklist가 있으면
   checklist를, 없으면 currentStatus 한 줄을 보여준다. */
function renderStepStatus(data) {
  const el = $("stepStatus");
  if (data.statusChecklist && data.statusChecklist.length) {
    el.hidden = false;
    el.innerHTML = data.statusChecklist.map(([label, state]) => {
      const icon = state === "done" ? "✓" : state === "failed" ? "×" : "…";
      return `<div class="step-status-item ${state}"><span class="icon">${icon}</span><span>${label}</span></div>`;
    }).join("");
  } else if (data.currentStatus) {
    el.hidden = false;
    el.innerHTML = `<p class="step-status-line"><span class="arrow">현재 →</span>${data.currentStatus}</p>`;
  } else {
    el.hidden = true; el.innerHTML = "";
  }
}
function renderStepNext(data) {
  const el = $("stepNext");
  el.hidden = !data.nextAction;
  el.innerHTML = data.nextAction ? `<span class="arrow">다음 →</span>${data.nextAction}` : "";
}
/* narrationPoints가 있으면 번호 흐름으로, 없으면 기존 narration 한 문단으로 보여준다. */
function renderNarration(data) {
  const points = $("narrationPoints"), plain = $("narration");
  points.hidden = !data.narrationPoints;
  plain.hidden = !!data.narrationPoints;
  points.innerHTML = data.narrationPoints ? data.narrationPoints.map((line) => `<li><span>${line}</span></li>`).join("") : "";
  plain.textContent = data.narrationPoints ? "" : data.narration;
}
function render() {
  const data = currentStep();
  populateSelects();
  renderScenarioButtons();
  $("chapterQuestion").textContent = currentChapter().title;
  $("chapterSubtitle").textContent = currentChapter().subtitle;
  const origin = currentChapter().summary;
  $("chapterOrigin").innerHTML = `<dt>왜 생겼는가</dt><dd>${origin.why}</dd><dt>어떻게 해결했는가</dt><dd>${origin.how}</dd>`;
  renderCanvas(data); renderRetryPolicy(data); renderStoreCompare(data); renderComparison(data); renderPerformance(data); renderKafkaPartitions(data);
  renderDetails(data); renderCodeStepper(); renderCodeView(data);
  $("stepTitle").textContent = data.action; $("counter").textContent = `Step ${state.step + 1} / ${currentScenario().steps.length}`;
  renderNarration(data); renderStepStatus(data); renderStepNext(data);
  $("stepFact").innerHTML = `<p class="fact-caption">${factCaption(data)}</p>${quickState(data)}${promptBlockText(data)}`;
  renderPerformanceHighlights();
  renderDecisionHighlights();
  syncPlaybackPadding();
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
  { chapter: "hotpath-performance", scenario: "batch-optimization", whatStep: "batch-fix", measureSteps: ["same-load-result", "stress-result"],
    summary: "TimeSlot 목록 조회가 회차마다 활성 예약·참여 인원 합계·CLOSED 여부·READY 선점 합계를 각각 질의해 3 + N×4 패턴(N=20 기준 83 SQL)으로 커넥션을 점유하던 구조를, timeSlotIds를 IN 절로 묶은 집계 쿼리 4개로 대체해 회차 수와 무관한 고정 7 SQL로 바꿨다. 인덱스 추가나 캐시 도입 없이 쿼리 횟수만 줄인 변경이다." },
  { chapter: "kafka-mechanics", scenario: "kafka-adoption-decision", whatStep: "after-message-id-key", measureSteps: ["after-message-id-key"],
    summary: "Partition Key를 chatRoomId에서 messageId로 교체해 동일 채팅방 메시지가 단일 partition에 집중되던 Hot-Key를 해소했다. AI 판정이 메시지 단건 독립 연산이라 방 단위 순서 보장이 도메인 계약상 불필요하다는 검토가 선행됐고, 그 결과 Consumer concurrency 3이 실제로 3개 partition을 병렬 소비하게 됐다." }
];
function renderPerformanceHighlights() {
  const cards = PERFORMANCE_HIGHLIGHTS.filter((card) => card.chapter === currentChapter().id);
  $("performanceOutcome").hidden = cards.length === 0;
  $("performanceHighlights").innerHTML = cards.map((card) => {
    const whatStep = findStep(card.chapter, card.scenario, card.whatStep);
    const rows = card.measureSteps.flatMap((id) => findStep(card.chapter, card.scenario, id).performance.filter((row) => row.improvement));
    const glossary = card.measureSteps.map((id) => findStep(card.chapter, card.scenario, id).metricGlossary).find(Boolean);
    return `<article class="perf-highlight"><h3>${whatStep.action}</h3><p>${card.summary || whatStep.narration}</p><div class="performance">${rows.map(performanceRowHtml).join("")}${metricGlossaryHtml(glossary)}</div></article>`;
  }).join("");
}
/* Step 패널은 비개발자용 쉬운 설명이지만, 이 요약은 "무엇을 어떤 메커니즘으로 해결했는가"를
   실제 컴포넌트·정책 이름으로 기술한다 — title/body를 여기서 직접 쓰고 step narration을 재사용하지 않는다. */
const DECISION_HIGHLIGHTS = [
  { chapter: "outbox", scenario: "chatroom-outbox", step: "retry",
    title: "메모리 기반 @TransactionalEventListener(AFTER_COMMIT) → Transactional Outbox 전환",
    body: "V2는 결제·예약 커밋 직후 Spring 이벤트 리스너가 같은 JVM 메모리 위에서 채팅방 생성을 수행했다. 이 실행 요청은 어디에도 영속화되지 않으므로 리스너가 예외로 종료되거나 인스턴스가 내려가면 '무엇을 다시 해야 하는지'를 복구할 근거 자체가 남지 않는다. V3는 같은 트랜잭션 안에서 outbox_event 행(status=PENDING)을 함께 커밋해 실행 요청을 DB에 영속화한다. 커밋 직후에는 ChatRoomOutboxProcessor.signal()이 즉시 호출돼 대부분 곧바로 처리되고, 별도 ChatRoomOutboxScheduler가 5초 주기로 폴링하며 signal 유실·인스턴스 재시작에 대비한 안전망 역할을 한다. 두 경로 모두 결국 Processor가 조건부 UPDATE로 claim(PENDING→PROCESSING)해 실행한다. 실패 시 attempt_count를 올리고 next_attempt_at을 5·10·20·40·80초 지수 backoff로 재예약하며, MAX_RETRIES(5) 초과 시 FAILED로 종료한다." },
  { chapter: "kafka-ai", scenario: "publish-failure", step: "retry",
    title: "Kafka publish 실패의 재시도 책임을 Outbox Processor가 보유",
    body: "ChatMessage 커밋과 Kafka publish는 하나의 원자적 단위가 아니다. publish가 실패해도 outbox_event 행이 PENDING으로 남아 있으므로 ChatMessageOutboxProcessor가 backoff 재예약 후 재발행한다. 메시지 본문은 이미 커밋된 ChatMessage가 Source of Truth이며 Outbox는 '아직 발행되지 않은 발행 요청'만 보관한다." },
  { chapter: "kafka-ai", scenario: "retry-exhausted-dlt", step: "dlt",
    title: "Consumer 재시도 소진 시 DLT 격리로 partition head-of-line blocking 방지",
    body: "DefaultErrorHandler에 FixedBackOff(최초 처리 포함 최대 3회)를 적용하고, 소진된 레코드는 ChatModerationDltRecoverer가 DLT 토픽으로 넘긴 뒤 ChatModerationService.recordFinalFailure로 ANALYSIS_FAILED를 기록한다. 실패 레코드가 같은 partition의 후속 offset 진행을 막지 않게 하는 것이 목적이며, CustomException·InvalidChatMessageEventException처럼 재시도로 해결되지 않는 예외는 notRetryable로 지정해 즉시 DLT로 보낸다." },
  { chapter: "redis", scenario: "aws-cross-instance-normal", step: "cross-instance",
    title: "다중 인스턴스 STOMP 세션 분산 문제를 Redis Pub/Sub fan-out으로 해소",
    body: "STOMP 세션은 각 애플리케이션 인스턴스의 로컬 메모리에 있으므로 인스턴스 A가 처리한 메시지는 인스턴스 B의 구독자에게 직접 전달되지 않는다. 커밋 후 RedisChatMessagePublisher가 공용 채널로 publish하고, 각 인스턴스의 RedisChatMessageSubscriber가 수신해 자기 인스턴스의 SimpMessagingTemplate으로만 fan-out한다. 이 경로는 best-effort이며 durable queue가 아니다 — 전달 보장은 DB cursor 재조회 계약이 담당한다." },
  { chapter: "kafka-mechanics", scenario: "kafka-adoption-decision", step: "reliability",
    title: "Kafka 채택 근거는 처리 지연 단축이 아니라 실패 경계 분리",
    body: "@Async ThreadPoolExecutor Baseline은 큐가 in-memory라 프로세스 종료 시 대기 중이던 작업이 analyze() 호출 없이 소실되고, 큐 포화 시 RejectedExecutionHandler가 재시도·DLT 없이 폐기한다. Kafka는 Broker가 offset 기준으로 이벤트를 보존하므로 Consumer 중단 중 적체된 15건이 재개 후 전량 처리됐고(lost 0, 복구 7.8초), Retry·DLT·Consumer scaling 경계를 함께 얻는다. 단건 latency는 오히려 Async가 빨랐다 — 속도 목적 채택은 실측으로 기각됐다." },
  { chapter: "ai-moderation", scenario: "clear-flagged-fast-path", step: "persisted",
    title: "고신뢰 패턴 Rule Fast Path로 LLM 호출·토큰 절감",
    body: "ModerationRuleFilter.clearFlagged가 개인 연락처+개인 문맥, 정확 일치 욕설, 투자·대출 유도 스팸 중 정확히 한 family만 매칭될 때 LLM을 호출하지 않고 FLAGGED를 확정한다(복수 signal 충돌 시에는 판단을 위임). #251 실측 기준 LLM Calls 88→72(-18.2%), Total Tokens 66,766→54,565(-18.3%), Rule Fast Path Precision 16/16(FP 0). 전체 Result Accuracy는 62/66→61/66이므로 '판정 정확도 개선'이 아니라 'Rule 귀책 regression 없이 호출·비용을 줄였다'로 한정한다." },
  { chapter: "ai-moderation", scenario: "why-not-context-llm", step: "rejected-decision",
    title: "Context LLM 미채택 — 최근 대화 전달은 Rule 판정 입력으로만 한정",
    body: "최근 대화 맥락을 Provider에 함께 전달하는 실험 경로는 분할 욕설·개인 연락처 탐지에는 유효했으나 공개 사업장 전화번호를 PERSONAL_INFORMATION으로 판정하는 False Positive가 관측됐다(#266 Provider 6-case). 따라서 DB Context는 ModerationRuleFilter.clearSplitFlagged의 bounded canonicalization 입력으로만 사용하고, Provider에는 현재 메시지 단건만 전달하는 것을 production 계약으로 확정했다." }
];
function renderDecisionHighlights() {
  const cards = DECISION_HIGHLIGHTS.filter((ref) => ref.chapter === currentChapter().id);
  $("reliabilityOutcome").hidden = cards.length === 0;
  $("reliabilityHighlights").innerHTML = cards.map((ref) => {
    const step = findStep(ref.chapter, ref.scenario, ref.step);
    return `<article><h3>${ref.title}</h3><p>${ref.body}</p>${step.decisionBadge ? `<p class="decision">${step.decisionBadge}</p>` : ""}</article>`;
  }).join("");
  $("outcomes").hidden = $("performanceOutcome").hidden && $("reliabilityOutcome").hidden;
}
function stop() { clearInterval(state.timer); state.timer = null; }
function resetStep() { stop(); state.step = 0; render(); }
function advance() { if (state.step >= currentScenario().steps.length - 1) { stop(); state.step = 0; render(); return; } state.step++; render(); }
$("chapterSelect").onchange = (event) => { state.chapter = Number(event.target.value); state.scenario = 0; resetStep(); };
$("scenarioButtons").onclick = (event) => {
  const button = event.target.closest("button[data-scenario]"); if (!button) return;
  state.scenario = Number(button.dataset.scenario); resetStep();
};
window.addEventListener("resize", () => { syncPlaybackPadding(); syncTopbarHeightVar(); });
$("play").onclick = () => { if (state.step === currentScenario().steps.length - 1) state.step = 0; stop(); render(); state.timer = setInterval(advance, Number($("speed").value)); }; $("pause").onclick = stop; $("next").onclick = advance; $("prev").onclick = () => { stop(); state.step = Math.max(0, state.step - 1); render(); }; $("reset").onclick = resetStep; $("speed").onchange = () => { if (state.timer) $("play").click(); };
$("expandCanvas").onclick = expandCanvasView;
$("collapseCanvas").onclick = collapseCanvasView;
$("canvasOverlay").onclick = (event) => { if (event.target === $("canvasOverlay")) collapseCanvasView(); };
/* 재생바를 키보드와 연동한다 — ← 이전, → 다음, Space 재생/정지 토글, Esc로 크게 보기 닫기.
   select/input에 포커스가 있을 때는 그 컨트롤의 기본 키 동작(값 변경, 스크롤)을 막지 않는다. */
function togglePlay() { (state.timer ? $("pause") : $("play")).click(); }
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !$("canvasOverlay").hidden) { collapseCanvasView(); return; }
  const focusedTag = document.activeElement.tagName;
  if (focusedTag === "SELECT" || focusedTag === "INPUT" || focusedTag === "TEXTAREA") return;
  if (event.key === "ArrowLeft") { event.preventDefault(); $("prev").click(); }
  else if (event.key === "ArrowRight") { event.preventDefault(); $("next").click(); }
  else if (event.code === "Space" || event.key === " ") { event.preventDefault(); togglePlay(); }
});
syncTopbarHeightVar();
render();
