<template>
  <!-- Flat line-art loop: a request travels in, a rule matches, a response comes back.
       Pure SVG + CSS keyframes, no images, and it degrades to a still diagram. -->
  <figure class="login-motion">
    <svg class="login-motion__canvas" viewBox="0 0 560 280" role="img" aria-label="请求进入、命中匹配规则并立即返回响应的示意动画">
      <!-- request -->
      <g class="motion-card motion-card--request">
        <rect x="12" y="112" width="150" height="96" rx="12" />
        <rect class="motion-chip" x="26" y="126" width="52" height="20" rx="6" />
        <text class="motion-chip__label motion-chip__label--get" x="52" y="140">GET</text>
        <text class="motion-key" x="26" y="166">/orders/:id</text>
        <rect class="motion-line" x="26" y="178" width="112" height="6" rx="3" />
        <rect class="motion-line motion-line--short" x="26" y="190" width="72" height="6" rx="3" />
      </g>

      <!-- inbound wire -->
      <path class="motion-wire" d="M162 160H238" />
      <circle class="motion-dot motion-dot--in" cx="162" cy="160" r="4.5" />

      <!-- matcher -->
      <g class="motion-hub">
        <circle class="motion-hub__ring" cx="280" cy="160" r="42" />
        <circle class="motion-hub__core" cx="280" cy="160" r="30" />
        <text class="motion-hub__mark" x="280" y="169">M</text>
        <g class="motion-rules">
          <rect class="motion-rule" x="243" y="214" width="30" height="8" rx="4" />
          <rect class="motion-rule motion-rule--2" x="279" y="214" width="30" height="8" rx="4" />
          <rect class="motion-rule motion-rule--3" x="315" y="214" width="24" height="8" rx="4" />
        </g>
        <text class="motion-caption" x="280" y="248">匹配规则</text>
      </g>

      <!-- outbound wire -->
      <path class="motion-wire" d="M322 160H398" />
      <circle class="motion-dot motion-dot--out" cx="322" cy="160" r="4.5" />

      <!-- response -->
      <g class="motion-card motion-card--response">
        <rect x="398" y="100" width="150" height="120" rx="12" />
        <rect class="motion-status" x="412" y="114" width="52" height="20" rx="6" />
        <text class="motion-status__label" x="438" y="128">200</text>
        <text class="motion-key" x="412" y="156">{</text>
        <text class="motion-key motion-key--indent" x="422" y="172">"code": 0,</text>
        <text class="motion-key motion-key--indent" x="422" y="188">"ok": true</text>
        <text class="motion-key" x="412" y="204">}</text>
        <rect class="motion-caret" x="421" y="196" width="2" height="12" rx="1" />
      </g>
    </svg>
    <figcaption class="login-motion__caption">一次请求：进入工作区 → 命中规则 → 立刻拿到响应</figcaption>
  </figure>
</template>

<style lang="scss" scoped>
.login-motion {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.login-motion__canvas {
  width: 100%;
  max-width: 560px;
  height: auto;
  display: block;
  overflow: visible;
}

.login-motion__caption {
  font-size: var(--app-font-caption);
  line-height: 1.6;
  color: var(--app-text-secondary);
  max-width: 420px;
}

// Cards are opaque surfaces outlined by colour, as the flat theme requires.
.motion-card rect:first-child {
  fill: var(--app-bg-surface);
  stroke: var(--app-divider);
  stroke-width: 1;
}

.motion-chip {
  fill: var(--el-color-success-light-9);
}

.motion-chip__label,
.motion-status__label {
  font-family: var(--app-font-mono);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: .04em;
  text-anchor: middle;
}

.motion-chip__label--get {
  fill: var(--app-text-success);
}

.motion-key {
  fill: var(--app-text-secondary);
  font-family: var(--app-font-mono);
  font-size: 12px;
}

.motion-key--indent {
  fill: var(--app-text-regular);
}

.motion-line {
  fill: var(--app-bg-hover);
}

.motion-line--short {
  fill: var(--app-divider);
}

.motion-status {
  fill: var(--el-color-success-light-9);
}

.motion-caret {
  fill: var(--app-accent);
  animation: caret-blink 1.1s steps(1, end) infinite;
}

.motion-wire {
  fill: none;
  stroke: var(--app-divider);
  stroke-width: 1.5;
  stroke-dasharray: 4 5;
}

.motion-dot {
  fill: var(--app-accent);
  opacity: 0;
}

.motion-hub__ring {
  fill: none;
  stroke: var(--app-accent);
  stroke-width: 1;
  opacity: .28;
}

.motion-hub__core {
  fill: var(--app-primary);
}

.motion-hub__mark {
  fill: var(--app-text-inverse);
  font-family: var(--app-font-sans);
  font-size: 20px;
  font-weight: 700;
  text-anchor: middle;
}

.motion-rule {
  fill: var(--app-divider);
}

.motion-caption {
  fill: var(--app-text-secondary);
  font-size: var(--app-font-caption);
  text-anchor: middle;
}

// The loop: dot in (0-30%), rule match (30-52%), dot out (52-70%), response settles (70-100%).
.motion-dot--in {
  animation: travel-in 7.2s cubic-bezier(.4, 0, .2, 1) infinite;
}

.motion-dot--out {
  animation: travel-out 7.2s cubic-bezier(.4, 0, .2, 1) infinite;
}

.motion-hub__ring {
  transform-box: fill-box;
  transform-origin: center;
  animation: ring-pulse 7.2s ease-out infinite;
}

.motion-hub__core {
  transform-box: fill-box;
  transform-origin: center;
  animation: hub-beat 7.2s ease-in-out infinite;
}

.motion-rule {
  animation: rule-hit 7.2s linear infinite;
}

.motion-rule--2 {
  animation-delay: .12s;
}

.motion-rule--3 {
  animation-delay: .24s;
}

.motion-card--response {
  animation: response-settle 7.2s ease-out infinite;
  transform-box: fill-box;
  transform-origin: center;
}

.motion-status {
  animation: status-pop 7.2s ease-out infinite;
  transform-box: fill-box;
  transform-origin: center;
}

@keyframes travel-in {
  0% { transform: translateX(0); opacity: 0; }
  4% { opacity: 1; }
  26% { transform: translateX(76px); opacity: 1; }
  30%, 100% { transform: translateX(76px); opacity: 0; }
}

@keyframes travel-out {
  0%, 52% { transform: translateX(0); opacity: 0; }
  56% { opacity: 1; }
  68% { transform: translateX(76px); opacity: 1; }
  72%, 100% { transform: translateX(76px); opacity: 0; }
}

@keyframes ring-pulse {
  0%, 26% { transform: scale(.86); opacity: .18; }
  36% { transform: scale(1.06); opacity: .5; }
  46%, 100% { transform: scale(1); opacity: .22; }
}

@keyframes hub-beat {
  0%, 28% { transform: scale(1); }
  34% { transform: scale(1.06); }
  40%, 100% { transform: scale(1); }
}

@keyframes rule-hit {
  0%, 30% { fill: var(--app-divider); }
  34%, 44% { fill: var(--app-accent); }
  50%, 100% { fill: var(--app-divider); }
}

@keyframes response-settle {
  0%, 70% { transform: translateY(4px); opacity: .35; }
  78%, 92% { transform: translateY(0); opacity: 1; }
  100% { transform: translateY(0); opacity: .35; }
}

@keyframes status-pop {
  0%, 74% { transform: scale(.9); }
  80% { transform: scale(1.04); }
  86%, 100% { transform: scale(1); }
}

@keyframes caret-blink {
  0%, 45% { opacity: 1; }
  46%, 100% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .motion-dot,
  .motion-dot--in,
  .motion-dot--out,
  .motion-hub__ring,
  .motion-hub__core,
  .motion-rule,
  .motion-card--response,
  .motion-status,
  .motion-caret {
    animation: none;
  }

  .motion-card--response {
    opacity: 1;
  }
}

// Below 900px the drawing scales under its own text; keep the moving shapes and drop
// the labels rather than render 7px type.
@media (max-width: 900px) {
  .login-motion__canvas {
    max-width: 420px;
  }

  .login-motion__caption,
  .motion-key,
  .motion-chip__label,
  .motion-status__label,
  .motion-caption {
    display: none;
  }
}
</style>
