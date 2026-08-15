/* Playback and rendering only. Every visual decision comes from step.visual. */
const $ = (id) => document.getElementById(id);
const state = { chapter: 0, scenario: 0, step: 0, timer: null, mode: "chapter", showcaseTab: "service", showcaseScenario: 0, showcaseStep: 0 };
const currentChapter = () => chapters[state.chapter];
const currentScenario = () => currentChapter().scenarios[state.scenario];
const currentStep = () => currentScenario().steps[state.step];
/* Ch6는 서버 topology 대신 moderationTopology(판정 경로)를 쓴다. Ch0 Showcase의 서비스/인프라 흐름도
   같은 원리로 자기 topologyKey를 쓴다. 전부 같은 canvas-node/connector/token 렌더러를 재사용한다 —
   별도 renderer를 새로 만들지 않는다. */
const TOPOLOGY_BY_KEY = {
  moderation: moderationTopology, "payment-followup": paymentFollowupTopology,
  "service-user": serviceUserTopology, "service-owner": serviceOwnerTopology, "service-auto": serviceAutoTopology,
  infra: infraTopology
};
const topologyFor = (data) => TOPOLOGY_BY_KEY[data.topologyKey] || topology;
function populateSelects() {
  const showcaseOption = `<option value="showcase">Ch0 · BobFull System Showcase</option>`;
  $("chapterSelect").innerHTML = showcaseOption + chapters.map((item, i) => `<option value="${i}">${item.shortLabel}</option>`).join("");
  $("chapterSelect").value = state.mode === "showcase" ? "showcase" : String(state.chapter);
}
/* 일반 Chapter 전용 섹션을 한 번에 켜고 끈다 — Ch0 Showcase는 완전히 다른 한 화면 레이아웃이라
   기존 stage/code-view/outcomes/evidence-gate 등을 그대로 감추고 별도 뷰를 보여준다. */
function applyModeVisibility() {
  const isShowcase = state.mode === "showcase";
  document.querySelectorAll(".chapter-only").forEach((el) => { el.hidden = isShowcase; });
  $("showcaseView").hidden = !isShowcase;
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
/* 모든 메시지가 Outbox/Kafka/Redis를 전부 순서대로 지나간다는 오해를 막기 위해, 서로 다른 책임
   영역을 아주 옅은 배경+작은 라벨로만 구분한다. active/dim 강조는 건드리지 않는다. */
function regionBgSvg(t) {
  if (!t.regions) return "";
  return t.regions.map((region) => `<g class="region"><rect class="region-bg" x="${region.x}" y="${region.y}" width="${region.w}" height="${region.h}" rx="10"/><text class="region-label" x="${region.x + 10}" y="${region.y + 16}">${region.label}</text></g>`).join("");
}
function renderCanvas(data) {
  const v = data.visual;
  const t = topologyFor(data);
  const regionSvg = regionBgSvg(t);
  const edgeSvg = Object.entries(t.edges).map(([id, path]) => `<path id="edge-${id}" class="connector ${v.activeEdges.includes(id) ? "active" : "dim"}" d="${path}"/>`).join("");
  const tokenSvg = v.activeEdges.map((id) => tokenSvgFor(t, id, v.token)).join("");
  const labelSvg = v.activeEdges.map((id) => edgeLabelSvg(t, id, v.token, v.edgeLabels && v.edgeLabels[id])).join("");
  const nodesSvg = t.nodes.map(([id, label]) => {
    const [x, y] = t.nodePositions[id];
    const active = v.activeNodes.includes(id);
    const committed = !active && v.committedNodes.includes(id);
    const cls = active ? "active" : committed ? "committed" : "dim";
    const text = committed ? `${label} ✓` : label;
    /* async(Async Queue)는 실제 운영 경로가 아니라 Ch5 실험 비교용 baseline이므로, 활성 상태여도
       점선 테두리와 보조 라벨로 "이건 비교 기준선이다"를 항상 구분해서 보여준다. */
    if (id === "async") {
      return `<g class="canvas-node ${cls} baseline" transform="translate(${x} ${y})"><rect width="100" height="70" rx="6"/><text x="50" y="34" font-weight="600">${text}</text><text x="50" y="50" class="node-sublabel">비교 기준</text></g>`;
    }
    /* 박스(100 너비, 좌우 여백 감안 84)보다 긴 라벨은 textLength로 압축해 테두리 밖으로 넘치지 않게 한다. */
    const compress = text.length > 9 ? ` textLength="84" lengthAdjust="spacingAndGlyphs"` : "";
    return `<g class="canvas-node ${cls}" transform="translate(${x} ${y})"><rect width="100" height="70" rx="6"/><text x="50" y="42"${compress}>${text}</text></g>`;
  }).join("");
  const badgeSvg = badgeSvgFor(t, v.badge);
  $("canvas").innerHTML = `<svg class="topology" viewBox="${t.viewBox}" role="img" aria-label="현재 Chapter의 고정 흐름 topology — 활성 path 위의 token만 이동한다"><g class="regions">${regionSvg}</g><g class="connectors">${edgeSvg}</g><g class="edge-labels">${labelSvg}</g><g class="tokens">${tokenSvg}</g><g class="nodes">${nodesSvg}</g>${badgeSvg}</svg><div class="flow-outcome ${v.outcome || ""}">${v.outcome ? `${outcomeLabel(v.outcome)} · ${data.action}` : ""}</div>`;
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
/* href가 없는 근거(#274처럼 아직 merge 전이라 저장소 경로가 없는 문서)는 broken link를 만들지 않고
   일반 caption 텍스트로만 보여준다. */
function linked(refs) {
  return refs.map((item) => item.href
    ? `<a href="${item.href}" target="_blank" rel="noreferrer">${item.label}</a>`
    : `<span class="evidence-plain">${item.label}</span>`).join("<br>");
}
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
  populateSelects();
  applyModeVisibility();
  if (state.mode === "showcase") { renderShowcaseStep(); return; }
  const data = currentStep();
  renderScenarioButtons();
  $("chapterQuestion").textContent = currentChapter().title;
  $("chapterSubtitle").textContent = currentChapter().subtitle;
  const origin = currentChapter().summary;
  $("originShort").innerHTML = `<strong>문제</strong> ${origin.problem} <strong>해결</strong> ${origin.solution}`;
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
  { chapter: "kafka-mechanics", scenario: "kafka-adoption-decision", step: "kafka-verdict",
    title: "Kafka 채택 근거 재정의(#274) — 처리 지연도, 유일한 유실 방지 수단도 아니라 운영 경계",
    body: "#192의 기존 비교는 Outbox 없는 Memory Async와 Outbox+Kafka를 비교해, Outbox가 주는 내구성과 Kafka가 주는 효과가 분리되지 않았다. #274는 같은 Transactional Outbox 조건에서 Outbox+Async와 Outbox+Kafka를 다시 비교했다: drain median은 Async 5.394s, Kafka 7.210s로 오히려 Async가 빨랐고(처리량 5.56 vs 4.16 msg/s), 실제 프로세스 강제 종료(destroyForcibly) 뒤에도 둘 다 lost=0·duplicate=0으로 복구됐다(재개까지 Async 296.825s, Kafka 40.614s — 이 값은 Spring 재기동을 포함한 end-to-end 값이며 'Kafka만의 복구시간'이 아니다). 차이는 durability 유무가 아니라 복구 경계였다 — Async는 DB PROCESSING row가 stale threshold 뒤 scheduler가 reclaim하고, Kafka는 Broker backlog를 Consumer Group이 이어받는다. 그래서 Kafka를 유지하는 근거는 속도나 유일한 유실 방지가 아니라, Consumer 이후의 적체·복구·관측·확장을 애플리케이션 내부 scheduler가 아닌 Broker/Consumer Group이라는 독립 경계로 분리하기 때문이다(Retry/DLT 효과 자체는 이번 crash 실험으로 검증하지 않았다)." },
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
/* Ch0 · BobFull System Showcase — 서비스/핵심 시스템/인프라 3개 관점(탭)으로 BobFull 전체를 보여준다.
   각 탭 안의 Scenario는 steps로 실제 데이터를 넘긴다: 이미 검증된 Chapter/Scenario/Step을
   {chapter, scenario, step} 참조로 재사용하거나(핵심 시스템 흐름의 AI 채팅 검수/AI 장애 대응/다중 서버
   채팅), Ch0 전용으로 새로 만든 실제 step() 객체 배열을 그대로 쓴다(결제 확정 후속 처리, 서비스 흐름,
   인프라 흐름 — scenario-data.js). 렌더링은 어느 경우든 findStep()/renderCanvas()를 그대로 재사용한다 —
   Showcase 전용 시각화 로직을 새로 만들지 않는다. problem/solution/outcomes 문구는 각 Step의
   factStatus/Evidence 범위를 벗어나지 않게 작성했다(과장 금지). */
const SHOWCASE_TABS = [
  { id: "service", label: "서비스 흐름", question: "BobFull은 어떻게 사용하는 서비스인가?" },
  { id: "core", label: "핵심 시스템 흐름", question: "복잡한 기능을 백엔드에서 어떻게 안전하게 처리했는가?" },
  { id: "infra", label: "인프라 흐름", question: "실제 요청은 어떤 인프라를 지나가는가?" }
];
const SHOWCASE_SCENARIOS_BY_TAB = {
  service: [
    { id: "service-user", title: "일반 사용자",
      problem: "일반 사용자에게 BobFull은 어떤 서비스인가?",
      solution: "식당·합석 탐색부터 결제, 참여자 채팅, 식사까지 한 흐름으로 이용합니다.",
      outcomes: ["예약부터 식사까지 한 서비스", "참여자와 미리 채팅으로 조율"], steps: serviceUserSteps },
    { id: "service-owner", title: "사장님",
      problem: "사장님에게 BobFull은 어떤 서비스인가?",
      solution: "식당·테이블·회차를 등록하고, 예약 현황과 지급 예정을 확인하며 운영합니다.",
      outcomes: ["예약 현황 실시간 확인", "정산 예정 금액 조회"], steps: serviceOwnerSteps },
    { id: "service-auto", title: "BobFull 자동 관리",
      problem: "합석 인원이 다 안 차면 예약은 어떻게 될까?",
      solution: "결제·참여 인원을 자동으로 누적하고, 성사 기준 충족 여부에 따라 확정하거나 취소·환불합니다.",
      outcomes: ["성사 기준 자동 판단", "기준 미달 시 자동 취소·환불"], steps: serviceAutoSteps }
  ],
  core: [
    { id: "payment-followup", title: "결제 확정 후속 처리",
      problem: "채팅방 생성이 실패하면 이미 끝난 결제·예약까지 함께 실패해야 할까?",
      solution: "핵심 거래(결제·예약·참여자)와 후속 기능(채팅방·이메일)의 실패 범위를 분리했습니다.",
      outcomes: ["핵심 거래 즉시 확정", "후속 기능은 각자 안전하게 재시도"], steps: paymentFollowupSteps },
    { id: "ai-moderation", title: "AI 채팅 검수",
      problem: "모든 메시지를 똑같이 LLM으로 보내야 할까?",
      solution: "명백한 위반은 Rule Filter가 즉시 판정하고, 애매한 경우만 LLM에 맡긴 뒤 결과를 검증해 저장합니다.",
      outcomes: ["확실한 것은 규칙으로 빠르게", "애매한 것만 LLM으로", "LLM 결과도 검증 후 저장"],
      steps: [
        { chapter: "kafka-ai", scenario: "normal", step: "send" },
        { chapter: "kafka-ai", scenario: "normal", step: "commit" },
        { chapter: "kafka-ai", scenario: "normal", step: "publish" },
        { chapter: "ai-moderation", scenario: "clear-flagged-fast-path", step: "rule-check" },
        { chapter: "ai-moderation", scenario: "clear-flagged-fast-path", step: "rule-hit" },
        { chapter: "ai-moderation", scenario: "llm-required", step: "rule-miss" },
        { chapter: "ai-moderation", scenario: "llm-required", step: "prompt-call" },
        { chapter: "ai-moderation", scenario: "llm-required", step: "persisted" }
      ] },
    { id: "ai-failure", title: "AI 장애 대응",
      problem: "AI 검수가 실패하면 채팅 저장까지 함께 실패해야 할까?",
      solution: "핵심 거래(메시지 저장)와 AI 후속 검수를 분리해, AI 장애가 메시지 저장에 영향을 주지 않게 격리했습니다.",
      outcomes: ["채팅 저장 완료", "AI 장애가 다른 메시지 처리를 막지 않음"],
      steps: [
        { chapter: "kafka-ai", scenario: "normal", step: "commit" },
        { chapter: "kafka-ai", scenario: "normal", step: "publish" },
        { chapter: "kafka-ai", scenario: "retry-exhausted-dlt", step: "retries" },
        { chapter: "kafka-ai", scenario: "retry-exhausted-dlt", step: "dlt" }
      ] },
    { id: "multi-instance", title: "다중 서버 채팅",
      problem: "사용자가 서로 다른 서버 인스턴스에 연결되어 있다면 메시지가 전달될까?",
      solution: "Redis Pub/Sub으로 서버 간 신호를 전파해, 접속한 인스턴스와 무관하게 메시지를 전달합니다.",
      outcomes: ["다중 인스턴스 메시지 전달 확인", "STOMP 세션 경계 극복"],
      steps: [
        { chapter: "redis", scenario: "local-two-instance-normal", step: "save" },
        { chapter: "redis", scenario: "local-two-instance-normal", step: "broadcast" },
        { chapter: "redis", scenario: "local-two-instance-normal", step: "fanout" }
      ] }
  ],
  infra: [
    { id: "infra-api", title: "일반 API",
      problem: "일반 API 요청은 실제로 어떤 인프라를 지나갈까?",
      solution: "Route 53 → ALB → 활성 Target Group → App EC2 → RDS 순서로 지나갑니다.",
      outcomes: ["실제 AWS Blue-Green 구성", "Auto Scaling은 아직 없음(#191)"], steps: infraSteps.api },
    { id: "infra-chat", title: "채팅",
      problem: "채팅 메시지는 서버가 달라도 전달될까?",
      solution: "Redis Pub/Sub으로 App EC2 간 신호를 전파해, 다른 서버에 접속한 사용자에게도 전달됩니다.",
      outcomes: ["다중 인스턴스 전달 확인(#169)", "STOMP 세션 경계 극복"], steps: infraSteps.chat },
    { id: "infra-moderation", title: "AI 검수",
      problem: "AI 검수 요청은 어떤 인프라를 지나갈까?",
      solution: "App EC2가 전용 Kafka EC2로 발행하면, AI Consumer가 소비해 외부 LLM을 호출합니다.",
      outcomes: ["Kafka 전용 EC2(단일 KRaft broker)", "외부 LLM(OpenAI) 호출"], steps: infraSteps.moderation },
    { id: "infra-deploy", title: "배포",
      problem: "새 버전은 어떻게 배포될까?",
      solution: "GitHub Actions가 ECR에 이미지를 올리고, SSM Run Command로 비활성 색 EC2에 배포한 뒤 헬스체크 통과 시 ALB 가중치를 전환합니다.",
      outcomes: ["무중단 Blue-Green 배포", "SSH 없이 SSM으로 배포"], steps: infraSteps.deploy }
  ]
};
function currentShowcaseTabScenarios() { return SHOWCASE_SCENARIOS_BY_TAB[state.showcaseTab]; }
function currentShowcase() { return currentShowcaseTabScenarios()[state.showcaseScenario]; }
/* steps 배열의 각 항목은 {chapter,scenario,step} 참조이거나(기존 Chapter 재사용), 이미 완성된 step()
   객체 그 자체다(Ch0 전용 신규 데이터) — 둘 다 findStep() 호출 없이도 같은 모양이 되도록 통일한다. */
function resolveShowcaseSteps(scenario) {
  return scenario.steps.map((item) => item.chapter ? findStep(item.chapter, item.scenario, item.step) : item);
}
function chapterIndexById(id) { return chapters.findIndex((item) => item.id === id); }
function scenarioIndexById(chapterIdx, scenarioId) { return chapters[chapterIdx].scenarios.findIndex((item) => item.id === scenarioId); }
function renderShowcaseMainTabs() {
  $("showcaseMainTabs").innerHTML = SHOWCASE_TABS.map((tab) =>
    `<button type="button" class="${tab.id === state.showcaseTab ? "active" : ""}" data-tab="${tab.id}">${tab.label}</button>`).join("");
  $("showcaseTabQuestion").textContent = SHOWCASE_TABS.find((tab) => tab.id === state.showcaseTab).question;
}
function renderShowcaseScenarioPicker() {
  $("showcaseTabs").innerHTML = currentShowcaseTabScenarios().map((scenario, i) =>
    `<button type="button" class="${i === state.showcaseScenario ? "active" : ""}" data-idx="${i}">${scenario.title}</button>`).join("");
}
/* renderCanvas(data)를 그대로 재사용한다 — 실제 step 객체를 넘기므로 active/dim 강조, token 이동,
   region 배경까지 일반 Chapter와 완전히 같은 렌더러로 그려진다. */
function renderShowcaseStep() {
  const scenario = currentShowcase();
  const steps = resolveShowcaseSteps(scenario);
  const data = steps[state.showcaseStep];
  renderShowcaseMainTabs();
  renderShowcaseScenarioPicker();
  $("showcaseScenarioTitle").textContent = scenario.title;
  $("showcaseProblem").textContent = scenario.problem;
  $("showcaseSolution").textContent = scenario.solution;
  renderCanvas(data);
  $("showcaseStepCounter").textContent = `Step ${state.showcaseStep + 1} / ${steps.length}`;
  $("showcaseOutcomes").innerHTML = scenario.outcomes.map((text) => `<span class="showcase-outcome">${text} ✓</span>`).join("");
  /* 서비스 흐름/인프라 흐름과 결제 후속 처리는 Ch0 전용 신규 데이터라 연결할 기존 상세 Chapter가 없다 —
     그 경우에만 "상세 문서로" 링크를 감춘다. */
  $("showcaseBackToDocs").hidden = !scenario.steps[0].chapter;
}
let showcaseTimer = null, showcaseHoldTimeout = null, showcaseStartTimeout = null, showcasePlaying = true;
function stopShowcaseTimers() { clearInterval(showcaseTimer); showcaseTimer = null; clearTimeout(showcaseHoldTimeout); clearTimeout(showcaseStartTimeout); }
function showcaseTick() {
  const scenario = currentShowcase();
  if (state.showcaseStep >= scenario.steps.length - 1) {
    clearInterval(showcaseTimer); showcaseTimer = null;
    showcaseHoldTimeout = setTimeout(() => {
      if (!showcasePlaying) return;
      state.showcaseStep = 0; renderShowcaseStep();
      showcaseTimer = setInterval(showcaseTick, 1600);
    }, 1800);
    return;
  }
  state.showcaseStep++; renderShowcaseStep();
}
function playShowcaseAuto() {
  showcasePlaying = true; $("showcaseAutoPlay").textContent = "⏸ Pause";
  clearTimeout(showcaseHoldTimeout);
  clearInterval(showcaseTimer);
  showcaseTimer = setInterval(showcaseTick, 1600);
}
function pauseShowcaseAuto() { showcasePlaying = false; $("showcaseAutoPlay").textContent = "▶ Auto Play"; stopShowcaseTimers(); }
/* 진입/Scenario 전환 직후 바로 재생하지 않고 0.5~1초 대기했다가 자동 재생을 시작한다. */
function restartShowcaseAutoplay() { stopShowcaseTimers(); showcaseStartTimeout = setTimeout(playShowcaseAuto, 700); }
function switchShowcaseScenario(idx) {
  state.showcaseScenario = idx; state.showcaseStep = 0;
  renderShowcaseStep();
  restartShowcaseAutoplay();
}
function switchShowcaseTab(tabId) {
  state.showcaseTab = tabId; state.showcaseScenario = 0; state.showcaseStep = 0;
  renderShowcaseStep();
  restartShowcaseAutoplay();
}
/* 같은 #canvas/#flowCaption 엘리먼트를 Showcase 슬롯으로 옮긴다 — 일반 Chapter의 크게 보기와
   같은 원리로, 다시 렌더링하지 않으므로 강조 상태가 그대로 유지된다. */
function enterShowcaseMode() {
  stop();
  if (!$("canvasOverlay").hidden) collapseCanvasView();
  state.mode = "showcase";
  $("showcaseCanvasSlot").appendChild($("canvas"));
  $("showcaseCanvasSlot").appendChild($("flowCaption"));
  render();
  restartShowcaseAutoplay();
}
function exitShowcaseMode(chapterIdx, scenarioIdx) {
  pauseShowcaseAuto();
  state.mode = "chapter";
  $("canvasSlot").appendChild($("canvas"));
  $("stageSticky").appendChild($("flowCaption"));
  state.chapter = chapterIdx != null && chapterIdx >= 0 ? chapterIdx : state.chapter;
  state.scenario = scenarioIdx != null && scenarioIdx >= 0 ? scenarioIdx : 0;
  state.step = 0;
  render();
}
/* README GIF·발표·브로셔 촬영용 — Showcase 컴포넌트를 새로 만들지 않고, 같은 화면에서
   조작 UI(Chapter/Scenario 선택, Replay/Pause, Capture 버튼 자체)만 감춘다. */
function setCaptureMode(on) {
  document.body.classList.toggle("capture-mode", on);
  const url = new URL(location.href);
  if (on) url.searchParams.set("capture", "true"); else url.searchParams.delete("capture");
  history.replaceState(null, "", url);
}
$("showcaseMainTabs").onclick = (event) => {
  const button = event.target.closest("button[data-tab]"); if (!button) return;
  switchShowcaseTab(button.dataset.tab);
};
$("showcaseTabs").onclick = (event) => {
  const button = event.target.closest("button[data-idx]"); if (!button) return;
  switchShowcaseScenario(Number(button.dataset.idx));
};
$("showcaseAutoPlay").onclick = () => { showcasePlaying ? pauseShowcaseAuto() : playShowcaseAuto(); };
$("showcaseReplay").onclick = () => { stopShowcaseTimers(); state.showcaseStep = 0; renderShowcaseStep(); playShowcaseAuto(); };
$("showcaseCapture").onclick = () => setCaptureMode(!document.body.classList.contains("capture-mode"));
$("showcaseBackToDocs").onclick = () => {
  const ref = currentShowcase().steps[0];
  if (!ref.chapter) return;
  const chapterIdx = chapterIndexById(ref.chapter);
  exitShowcaseMode(chapterIdx, scenarioIndexById(chapterIdx, ref.scenario));
};
function stop() { clearInterval(state.timer); state.timer = null; }
function resetStep() { stop(); state.step = 0; render(); }
function advance() { if (state.step >= currentScenario().steps.length - 1) { stop(); state.step = 0; render(); return; } state.step++; render(); }
$("chapterSelect").onchange = (event) => {
  const value = event.target.value;
  if (value === "showcase") { enterShowcaseMode(); return; }
  if (state.mode === "showcase") { exitShowcaseMode(Number(value), 0); return; }
  state.chapter = Number(value); state.scenario = 0; resetStep();
};
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
  if (event.key === "Escape" && document.body.classList.contains("capture-mode")) { setCaptureMode(false); return; }
  if (event.key === "Escape" && !$("canvasOverlay").hidden) { collapseCanvasView(); return; }
  if (state.mode === "showcase") return; /* Showcase는 자체 Auto Play/Replay를 쓰고, 일반 재생 단축키와 섞이지 않게 한다. */
  const focusedTag = document.activeElement.tagName;
  if (focusedTag === "SELECT" || focusedTag === "INPUT" || focusedTag === "TEXTAREA") return;
  if (event.key === "ArrowLeft") { event.preventDefault(); $("prev").click(); }
  else if (event.key === "ArrowRight") { event.preventDefault(); $("next").click(); }
  else if (event.code === "Space" || event.key === " ") { event.preventDefault(); togglePlay(); }
});
syncTopbarHeightVar();
/* README GIF 재촬영·발표 직전 특정 Scenario 바로 열기용 — /flow-lab/...?chapter=showcase&scenario=ai-failure&capture=true
   scenario id는 3개 탭에 걸쳐 찾는다(탭까지 함께 지정할 필요 없이 scenario id만으로 진입). */
function findShowcaseLocation(scenarioId) {
  for (const tab of SHOWCASE_TABS) {
    const idx = SHOWCASE_SCENARIOS_BY_TAB[tab.id].findIndex((item) => item.id === scenarioId);
    if (idx >= 0) return { tab: tab.id, idx };
  }
  return null;
}
const startupParams = new URLSearchParams(location.search);
if (startupParams.get("chapter") === "showcase") {
  const found = findShowcaseLocation(startupParams.get("scenario"));
  if (found) { state.showcaseTab = found.tab; state.showcaseScenario = found.idx; }
  enterShowcaseMode();
  if (startupParams.get("capture") === "true") setCaptureMode(true);
} else {
  render();
}
