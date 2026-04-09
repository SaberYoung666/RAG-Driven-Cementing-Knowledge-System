<template>
  <div class="chat-page">
    <a-alert
      v-if="!appStore.ragStatus.chatReady"
      class="chat-rag-alert"
      type="warning"
      show-icon
      :message="ragChatBlockedReason"
    />
    <main class="chat-main" ref="scrollRef">
      <div class="chat-container">
          <div v-if="messages.length === 0" class="empty">
            <div class="empty-title">开始提问</div>
            <div class="empty-sub">支持新建会话、查看历史、重命名和删除。</div>
            <div class="chips">
              <button class="chip" @click="quickAsk('固井顶替效率受哪些因素影响？')">固井顶替效率受哪些因素影响？</button>
              <button class="chip" @click="quickAsk('解释一下“环空死角”是什么意思？')">什么是环空死角？</button>
              <button class="chip" @click="quickAsk('给出固井施工常见异常的排查思路')">固井异常排查思路</button>
            </div>
          </div>

          <div v-for="m in messages" :key="m.id" class="msg-row" :class="m.role">
            <div class="avatar">
              <div v-if="m.role === 'user'" class="avatar-inner">我</div>
              <img v-else class="avatar-image" :src="helperHeadImg" alt="助手头像" />
            </div>

            <div class="bubble">
              <div class="meta">
                <span class="role">{{ m.role === "user" ? "你" : "助手" }}</span>
                <span class="time">{{ formatTime(m.ts) }}</span>
              </div>

              <div
                v-if="m.role === 'assistant'"
                class="content markdown"
                v-html="renderMarkdown(m.content)"
                @click="onAssistantContentClick(m, $event)"
              />
              <pre v-else class="content usertext">{{ m.content }}</pre>

              <!-- 展示引用 -->
              <div v-if="m.citations?.length" class="citations">
                <button class="cit-toggle" type="button" @click="toggleCitations(m)">
                  <span class="cit-title">引用</span>
                  <component :is="m.citationsOpen ? UpOutlined : DownOutlined" class="cit-toggle-icon" />
                </button>
                <div v-if="m.citationsOpen" class="cit-list">
                  <button
                    v-for="c in m.citations"
                    :key="c.evidence_id + c.chunk_id"
                    class="cit"
                    type="button"
                    @click="openEvidenceDetail(m, c)"
                  >
                    <div class="cit-left">
                      <span class="badge">{{ c.evidence_id }}</span>
                      <span class="cit-src">{{ c.source ?? "未知来源" }}</span>
                      <span class="cit-meta">
                        文档 {{ c.doc_id ?? "无" }} ·
                        页码 {{ c.page ?? "无" }}
                        <span v-if="c.section"> · {{ c.section }}</span>
                      </span>
                    </div>
                    <div class="cit-right">
                      <span class="score">分数 {{ toFixed(c.score) }}</span>
                      <span class="cit-link">查看详情</span>
                    </div>
                  </button>
                </div>
              </div>

              <div v-if="m.role === 'assistant' && m.status === 'thinking'" class="thinking">
                <span class="spinner" /> 正在生成…
              </div>
            </div>
          </div>
      </div>
    </main>

    <footer class="composer">
      <div class="composer-inner">
        <div class="composer-panel">
          <textarea ref="inputRef" v-model="input" class="textarea" :placeholder="isGenerating ? '生成中…' : (appStore.ragStatus.chatReady ? '有问题，尽管问' : 'RAG 正在准备中，请等待')"
            :disabled="isGenerating || !appStore.ragStatus.chatReady" rows="1" @keydown="onKeydown" @input="autoGrow" />

          <div class="composer-tools">
            <div class="tools-left">
              <a-popover v-model:open="settingsOpen" trigger="click" placement="topLeft" overlay-class-name="chat-settings-popover">
                <template #content>
                  <div class="settings-panel">
                    <div class="settings-title">对话配置</div>

                    <div class="settings-row">
                      <span class="settings-label">检索模式</span>
                      <a-select v-model:value="mode" class="settings-control" :options="modeOptions" />
                    </div>

                    <div class="settings-row">
                      <span class="settings-label">召回数</span>
                      <a-input-number v-model:value="topK" class="settings-control" :min="1" :max="30" />
                    </div>

                    <div class="settings-row">
                      <span class="settings-label">混合权重</span>
                      <a-input-number
                        v-model:value="alpha"
                        class="settings-control"
                        :min="0"
                        :max="1"
                        :step="0.05"
                        :disabled="mode !== 'hybrid'"
                      />
                    </div>

                    <div class="settings-row">
                      <span class="settings-label">启用重排</span>
                      <a-switch v-model:checked="rerankOn" />
                    </div>

                    <div class="settings-row">
                      <span class="settings-label">重排候选数</span>
                      <a-input-number
                        v-model:value="candidateK"
                        class="settings-control"
                        :min="5"
                        :max="60"
                        :disabled="!rerankOn"
                      />
                    </div>

                    <div class="settings-row">
                      <span class="settings-label">最低分数</span>
                      <a-input-number v-model:value="minScore" class="settings-control" :min="0" :max="1" :step="0.05" />
                    </div>
                  </div>
                </template>

                <button class="tool-btn" :class="{ active: settingsOpen }" type="button" aria-label="配置">
                  <SettingOutlined />
                </button>
              </a-popover>

              <button class="tool-think" :class="{ active: thinkEnabled }" type="button" aria-label="思考模式"
                :disabled="!appStore.ragStatus.chatReady" @click="toggleThinking">
                <BulbOutlined />
                <span>思考</span>
              </button>
            </div>

            <div class="tools-right">
              <button
                class="tool-btn"
                :class="{ active: voiceListening }"
                type="button"
                :aria-label="voiceListening ? '停止语音输入' : '语音输入'"
                @click="toggleVoiceInput"
              >
                <AudioOutlined />
              </button>
              <button class="send-fab" type="button" aria-label="发送" @click="send" :disabled="!canSend">
                <SendOutlined class="send-icon" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </footer>

    <a-modal
      v-model:open="evidenceModalOpen"
      title="证据详情"
      :footer="null"
      :width="900"
      wrap-class-name="evidence-modal-wrap"
    >
      <template v-if="selectedEvidence">
        <div class="evidence-detail">
          <div class="evidence-head">
            <div class="evidence-title-row">
              <span class="badge">{{ selectedEvidence.citation.evidence_id }}</span>
              <span class="evidence-title">引用证据</span>
            </div>
            <div class="evidence-score">相关度 {{ toFixed(selectedEvidence.citation.score) }}</div>
          </div>

          <div class="evidence-grid">
            <div class="evidence-meta-card">
              <div class="evidence-meta-label">来源</div>
              <div class="evidence-meta-value">{{ selectedEvidence.citation.source ?? selectedEvidence.retrieved?.source ?? "未知来源" }}</div>
            </div>
            <div class="evidence-meta-card">
              <div class="evidence-meta-label">文档 ID</div>
              <div class="evidence-meta-value mono">{{ selectedEvidence.citation.doc_id ?? selectedEvidence.retrieved?.doc_id ?? "-" }}</div>
            </div>
            <div class="evidence-meta-card">
              <div class="evidence-meta-label">页码</div>
              <div class="evidence-meta-value">{{ selectedEvidence.citation.page ?? selectedEvidence.retrieved?.page ?? "-" }}</div>
            </div>
            <div class="evidence-meta-card">
              <div class="evidence-meta-label">章节</div>
              <div class="evidence-meta-value">{{ selectedEvidence.citation.section ?? selectedEvidence.retrieved?.section ?? "-" }}</div>
            </div>
            <div class="evidence-meta-card evidence-meta-card-wide">
              <div class="evidence-meta-label">分块 ID</div>
              <div class="evidence-meta-value mono">{{ selectedEvidence.citation.chunk_id || selectedEvidence.retrieved?.chunkId || "-" }}</div>
            </div>
          </div>

          <div class="evidence-block">
            <div class="evidence-block-title">证据正文</div>
            <div v-if="selectedEvidence.retrieved?.text" class="evidence-body">
              {{ selectedEvidence.retrieved.text }}
            </div>
            <div v-else class="evidence-empty">
              当前可用数据中没有该证据的正文片段。新生成的回答如果后端返回 `retrieved` 内容，这里会显示对应文本；历史会话通常只保留引用摘要。
            </div>
          </div>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import dayjs from "dayjs";
import { message } from "ant-design-vue";
import { useRoute, useRouter } from "vue-router";
import { AudioOutlined, BulbOutlined, DownOutlined, SendOutlined, SettingOutlined, UpOutlined } from "@ant-design/icons-vue";
import MarkdownIt from "markdown-it";
import hljs from "highlight.js";
import { createSession, getSessionDetail, postChat } from "@/api/chat";
import { useAppStore } from "@/stores/app";
import type { Citation as ApiCitation, RetrievedChunk } from "@/types";
import helperHeadImg from "@/assets/helper_headimg.png";

type Role = "user" | "assistant";
type MsgStatus = "done" | "thinking" | "error";

type Citation = {
  evidence_id: string;
  score: number;
  doc_id?: string;
  chunk_id: string;
  source?: string;
  page?: number | string;
  section?: string;
};

type Message = {
  id: string;
  role: Role;
  content: string;
  ts: number;
  status: MsgStatus;
  citations?: Citation[];
  retrieved?: RetrievedEvidence[];
  citationsOpen?: boolean;
};

type RetrievedEvidence = RetrievedChunk & {
  doc_id?: string;
};

type EvidenceDetail = {
  citation: Citation;
  retrieved?: RetrievedEvidence;
};

const EVIDENCE_REF_RE = /【证据\d+】|（证据\d+）|\(证据\d+\)|\{证据\d+\}|\[证据\d+\]|证据\d+/g;
type MarkdownTokenCtor = new (...args: any[]) => any;

type SpeechRecognitionAlternative = {
  transcript: string;
  confidence: number;
};

type SpeechRecognitionResultLike = {
  isFinal: boolean;
  length: number;
  [index: number]: SpeechRecognitionAlternative;
};

type SpeechRecognitionResultListLike = {
  length: number;
  [index: number]: SpeechRecognitionResultLike;
};

type SpeechRecognitionEventLike = Event & {
  resultIndex: number;
  results: SpeechRecognitionResultListLike;
};

type SpeechRecognitionErrorEventLike = Event & {
  error: string;
  message?: string;
};

type SpeechRecognitionInstance = {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  maxAlternatives: number;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onstart: ((event: Event) => void) | null;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: ((event: Event) => void) | null;
};

type SpeechRecognitionCtor = new () => SpeechRecognitionInstance;

const route = useRoute();
const router = useRouter();
const appStore = useAppStore();
const scrollRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLTextAreaElement | null>(null);

const input = ref("");
const isGenerating = ref(false);
const thinkEnabled = ref(false);
const settingsOpen = ref(false);
const voiceListening = ref(false);
const voiceBaseInput = ref("");
const recognitionRef = ref<SpeechRecognitionInstance | null>(null);
const evidenceModalOpen = ref(false);
const selectedEvidence = ref<EvidenceDetail | null>(null);

const mode = ref<"dense" | "hybrid">("hybrid");
const topK = ref(6);
const alpha = ref(0.6);
const rerankOn = ref(true);
const candidateK = ref(20);
const minScore = ref(0.35);
const modeOptions = [
  { value: "dense", label: "向量检索" },
  { value: "hybrid", label: "混合检索" },
];

const messages = reactive<Message[]>([]);
const currentSessionId = ref("");

function escapeHtml(s: string) {
  return s
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeHtmlAttr(s: string) {
  return escapeHtml(s);
}

function normalizeEvidenceRefLabel(raw: string) {
  return raw.replace(/^[【（(\[{]/, "").replace(/[】）)\]}]$/, "");
}

function createTextToken(TokenCtor: MarkdownTokenCtor, content: string) {
  const token = new TokenCtor("text", "", 0);
  token.content = content;
  return token;
}

function createEvidenceInlineToken(TokenCtor: MarkdownTokenCtor, rawLabel: string) {
  const token = new TokenCtor("html_inline", "", 0);
  const evidenceId = normalizeEvidenceRefLabel(rawLabel);
  token.content =
    `<button class="evidence-inline-btn" type="button" data-evidence-id="${escapeHtmlAttr(evidenceId)}">` +
    `${escapeHtml(evidenceId)}` +
    `</button>`;
  return token;
}

function inlineEvidencePlugin(mdInstance: MarkdownIt) {
  mdInstance.core.ruler.push("inline_evidence_refs", (state) => {
    const TokenCtor = state.Token;
    for (const token of state.tokens) {
      if (token.type !== "inline" || !token.children?.length) continue;
      const nextChildren = [];
      for (const child of token.children) {
        if (child.type !== "text" || !child.content) {
          nextChildren.push(child);
          continue;
        }

        let lastIndex = 0;
        let matched = false;
        EVIDENCE_REF_RE.lastIndex = 0;
        for (const match of child.content.matchAll(EVIDENCE_REF_RE)) {
          const fullMatch = match[0];
          const index = match.index ?? 0;
          if (index > lastIndex) {
            nextChildren.push(createTextToken(TokenCtor, child.content.slice(lastIndex, index)));
          }
          nextChildren.push(createEvidenceInlineToken(TokenCtor, fullMatch));
          lastIndex = index + fullMatch.length;
          matched = true;
        }

        if (!matched) {
          nextChildren.push(child);
          continue;
        }

        if (lastIndex < child.content.length) {
          nextChildren.push(createTextToken(TokenCtor, child.content.slice(lastIndex)));
        }
      }
      token.children = nextChildren;
    }
  });
}

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(str: string, lang: string) {
    try {
      if (lang && hljs.getLanguage(lang)) {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang }).value}</code></pre>`;
      }
      return `<pre class="hljs"><code>${hljs.highlightAuto(str).value}</code></pre>`;
    } catch {
      return `<pre class="hljs"><code>${escapeHtml(str)}</code></pre>`;
    }
  },
});
md.use(inlineEvidencePlugin);

const canSend = computed(() => input.value.trim().length > 0 && !isGenerating.value && appStore.ragStatus.chatReady);
const ragChatBlockedReason = computed(() => appStore.ragStatus.message || "RAG 正在准备中，请稍候");

function renderMarkdown(text: string) {
  return md.render(text || "");
}

function formatTime(ts: number) {
  const d = new Date(ts);
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

function toFixed(x: unknown) {
  const n = Number(x);
  return Number.isFinite(n) ? n.toFixed(4) : "无";
}

function uid() {
  return Math.random().toString(16).slice(2) + Date.now().toString(16);
}

function scrollToBottom() {
  const el = scrollRef.value;
  if (!el) return;
  el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
}

function autoGrow() {
  const el = inputRef.value;
  if (!el) return;
  el.style.height = "0px";
  el.style.height = Math.min(el.scrollHeight, 180) + "px";
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    send();
  }
}

function toggleThinking() {
  thinkEnabled.value = !thinkEnabled.value;
}

function toggleCitations(msg: Message) {
  msg.citationsOpen = !msg.citationsOpen;
}

function resolveSpeechRecognitionCtor() {
  const speechWindow = window as Window & {
    SpeechRecognition?: SpeechRecognitionCtor;
    webkitSpeechRecognition?: SpeechRecognitionCtor;
  };
  return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null;
}

function mergeVoiceText(base: string, transcript: string) {
  const trimmed = transcript.trim();
  if (!trimmed) return base;
  if (!base) return trimmed;
  return `${base}${base.endsWith("\n") ? "" : "\n"}${trimmed}`;
}

function syncInputHeight() {
  nextTick(() => {
    autoGrow();
    inputRef.value?.focus();
  });
}

function stopVoiceInput() {
  recognitionRef.value?.stop();
}

function setupRecognition() {
  if (recognitionRef.value) return recognitionRef.value;
  const RecognitionCtor = resolveSpeechRecognitionCtor();
  if (!RecognitionCtor) return null;

  const recognition = new RecognitionCtor();
  recognition.lang = "zh-CN";
  recognition.interimResults = true;
  recognition.continuous = false;
  recognition.maxAlternatives = 1;

  recognition.onstart = () => {
    voiceListening.value = true;
  };

  recognition.onresult = (event) => {
    let transcript = "";
    for (let i = event.resultIndex; i < event.results.length; i += 1) {
      transcript += event.results[i][0]?.transcript ?? "";
    }
    input.value = mergeVoiceText(voiceBaseInput.value, transcript);
    syncInputHeight();
  };

  recognition.onerror = (event) => {
    voiceListening.value = false;
    if (event.error === "aborted") return;
    if (event.error === "not-allowed") {
      message.warning("浏览器未授予麦克风权限");
      return;
    }
    if (event.error === "no-speech") {
      message.warning("未识别到语音，请重试");
      return;
    }
    message.warning(`语音输入失败：${event.message || event.error}`);
  };

  recognition.onend = () => {
    voiceListening.value = false;
  };

  recognitionRef.value = recognition;
  return recognition;
}

function toggleVoiceInput() {
  if (voiceListening.value) {
    stopVoiceInput();
    return;
  }

  const recognition = setupRecognition();
  if (!recognition) {
    message.warning("当前浏览器不支持语音输入");
    return;
  }

  voiceBaseInput.value = input.value.trim();
  try {
    recognition.start();
  } catch {
    message.warning("语音输入暂时不可用，请稍后重试");
  }
}

function quickAsk(q: string) {
  input.value = q;
  nextTick(() => {
    autoGrow();
    send();
  });
}

function normalizeChunkId(value: any) {
  const raw = typeof value === "string" ? value.trim() : "";
  if (!raw) return "";
  const matched = raw.match(/p\d+::c\d+/);
  return matched?.[0] ?? raw;
}

// 格式化引用数据
function normalizeCitation(c: any): Citation {
  return {
    evidence_id: c?.evidence_id ?? c?.evidenceId ?? "证据",
    score: Number(c?.score ?? 0),
    doc_id: c?.doc_id ?? c?.docId,
    chunk_id: normalizeChunkId(c?.chunk_id ?? c?.chunkId),
    source: c?.source,
    page: c?.page,
    section: c?.section,
  };
}

function fromApiCitationList(citations?: ApiCitation[]) {
  return (citations ?? []).map((c: any) =>
    normalizeCitation({
      evidenceId: c?.evidenceId ?? c?.evidence_id,
      score: c.score,
      docId: c?.docId ?? c?.doc_id,
      chunkId: c?.chunkId ?? c?.chunk_id,
      source: c.source,
      page: c.page,
      section: c.section,
    })
  );
}

function normalizeRetrieved(item: any): RetrievedEvidence {
  const metadata = item?.metadata ?? {};
  return {
    rank: item?.rank,
    chunkId: normalizeChunkId(item?.chunkId ?? item?.chunk_id),
    score: Number(item?.score ?? 0),
    text: item?.text,
    source: item?.source ?? metadata?.source,
    page: item?.page ?? metadata?.page,
    section: item?.section ?? metadata?.section,
    metadata,
    doc_id: item?.doc_id ?? item?.docId ?? metadata?.doc_id ?? metadata?.docId,
  };
}

function fromApiRetrievedList(retrieved?: RetrievedChunk[]) {
  return (retrieved ?? []).map((item: any) => normalizeRetrieved(item));
}

function getRouteSessionId() {
  return typeof route.query.sessionId === "string" ? route.query.sessionId : "";
}

function applyDetailMessages(rawMessages: any[]) {
  messages.splice(0, messages.length);
  for (const m of rawMessages ?? []) {
    messages.push({
      id: String(m?.id ?? uid()),
      role: m?.role === "assistant" ? "assistant" : "user",
      content: String(m?.content ?? ""),
      ts: m?.createdAt && dayjs(m.createdAt).isValid() ? dayjs(m.createdAt).valueOf() : Date.now(),
      status: "done",
      citations: (m?.citations ?? []).map((c: any) => normalizeCitation(c)),
      retrieved: fromApiRetrievedList(m?.retrieved),
      citationsOpen: true,
    });
  }
}

function findRetrievedEvidence(message: Message, citation: Citation) {
  const normalizedCitationChunkId = normalizeChunkId(citation.chunk_id);
  return (message.retrieved ?? []).find((item) => normalizeChunkId(item.chunkId) === normalizedCitationChunkId);
}

function openEvidenceDetail(message: Message, citation: Citation) {
  selectedEvidence.value = {
    citation,
    retrieved: findRetrievedEvidence(message, citation),
  };
  evidenceModalOpen.value = true;
}

function openEvidenceDetailById(msg: Message, evidenceId: string) {
  const citation = (msg.citations ?? []).find((item) => item.evidence_id === evidenceId);
  if (!citation) {
    message.warning(`未找到 ${evidenceId} 的详情数据`);
    return;
  }
  openEvidenceDetail(msg, citation);
}

function onAssistantContentClick(msg: Message, event: MouseEvent) {
  const target = event.target as HTMLElement | null;
  const trigger = target?.closest(".evidence-inline-btn") as HTMLElement | null;
  if (!trigger) return;
  event.preventDefault();
  event.stopPropagation();
  const evidenceId = trigger.dataset.evidenceId;
  if (!evidenceId) return;
  openEvidenceDetailById(msg, evidenceId);
}

async function loadSessionDetail(sessionId: string) {
  if (!sessionId) {
    startNewConversation(false);
    return;
  }
  if (sessionId === currentSessionId.value && messages.length > 0) return;
  currentSessionId.value = sessionId;
  try {
    const resp = await getSessionDetail(sessionId);
    if (resp.code !== 0) {
      message.error(resp.message || "加载会话详情失败");
      return;
    }
    applyDetailMessages(resp.data?.messages ?? []);
    await nextTick();
    scrollToBottom();
  } catch {
    // 错误提示由拦截器统一处理
  }
}

function startNewConversation(focus = true) {
  currentSessionId.value = "";
  messages.splice(0, messages.length);
  input.value = "";
  nextTick(() => {
    autoGrow();
    if (focus) inputRef.value?.focus();
  });
}

async function ensureSession(question: string) {
  if (currentSessionId.value) return currentSessionId.value;
  const title = question.length > 24 ? `${question.slice(0, 24)}...` : question;
  const resp = await createSession({ title });
  if (resp.code !== 0 || !resp.data?.sessionId) {
    throw new Error(resp.message || "创建会话失败");
  }
  currentSessionId.value = resp.data.sessionId;
  await router.replace({ path: "/chat", query: { sessionId: currentSessionId.value } });
  window.dispatchEvent(new CustomEvent("chat-sessions-updated"));
  return currentSessionId.value;
}

async function send() {
  if (!appStore.ragStatus.chatReady) {
    message.warning(ragChatBlockedReason.value);
    return;
  }
  if (!canSend.value) return;

  const q = input.value.trim();
  input.value = "";
  nextTick(autoGrow);

  const userMsg: Message = {
    id: uid(),
    role: "user",
    content: q,
    ts: Date.now(),
    status: "done",
  };
  messages.push(userMsg);

  const assistantMsg: Message = {
    id: uid(),
    role: "assistant",
    content: "",
    ts: Date.now(),
    status: "thinking",
    citations: [],
    citationsOpen: true,
  };
  messages.push(assistantMsg);

  isGenerating.value = true;

  await nextTick();
  scrollToBottom();

  try {
    const sessionId = await ensureSession(q);
    const resp = await postChat({
      query: q,
      sessionId,
      topK: topK.value,
      mode: mode.value,
      alpha: mode.value === "hybrid" ? alpha.value : undefined,
      rerankOn: rerankOn.value,
      candidateK: rerankOn.value ? candidateK.value : undefined,
      minScore: minScore.value,
      useLlm: thinkEnabled.value,
    });

    if (resp.code !== 0 || !resp.data) {
      throw new Error(resp.message || "对话失败");
    }

    assistantMsg.content = resp.data.answer || "";
    assistantMsg.citations = fromApiCitationList(resp.data.citations);
    assistantMsg.retrieved = fromApiRetrievedList(resp.data.retrieved);
    assistantMsg.status = "done";
    window.dispatchEvent(new CustomEvent("chat-sessions-updated"));
  } catch (err: any) {
    assistantMsg.status = "error";
    assistantMsg.content = `请求失败：${err?.message ?? String(err)}`;
  } finally {
    isGenerating.value = false;
    await nextTick();
    scrollToBottom();
    inputRef.value?.focus();
  }
}

function handleNewConversation() {
  if (route.path === "/chat" && !getRouteSessionId()) {
    startNewConversation();
  }
}

watch(
  () => route.query.sessionId,
  (sessionId) => {
    if (typeof sessionId === "string" && sessionId) {
      loadSessionDetail(sessionId);
      return;
    }
    startNewConversation(false);
  },
  { immediate: true }
);

onMounted(() => {
  autoGrow();
  inputRef.value?.focus();
  window.addEventListener("chat-new-conversation", handleNewConversation);
});

onBeforeUnmount(() => {
  recognitionRef.value?.abort();
  window.removeEventListener("chat-new-conversation", handleNewConversation);
});
</script>

<style scoped>
.chat-page {
  --cp-bg: var(--bg);
  --cp-surface: var(--bg-elevated);
  --cp-surface-soft: var(--bg-soft);
  --cp-border: var(--border);
  --cp-text: var(--text);
  --cp-muted: var(--muted);
  --cp-accent: var(--accent);
  --cp-accent-rgb: var(--accent-rgb);
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: radial-gradient(1200px 600px at 50% -100px, rgba(var(--cp-accent-rgb), 0.14), transparent 60%), var(--cp-bg);
  color: var(--cp-text);
}

.chat-rag-alert {
  width: min(980px, calc(100% - 32px));
  margin: 0 auto 12px;
}

.chat-main {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 18px 0;
}

.chat-container {
  width: min(980px, calc(100% - 32px));
  margin: 0 auto;
}

.empty {
  margin: 120px auto 0;
  text-align: center;
  max-width: 520px;
  padding: 22px 18px;
  border: 1px solid var(--cp-border);
  border-radius: 16px;
  background: color-mix(in srgb, var(--cp-surface) 90%, transparent);
  backdrop-filter: blur(10px);
  box-shadow: var(--shadow);
}

.empty-title {
  font-size: 18px;
  font-weight: 700;
}

.empty-sub {
  margin-top: 10px;
  font-size: 13px;
  color: var(--cp-muted);
  line-height: 1.6;
}

.chips {
  margin-top: 16px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.chip {
  border: 1px solid var(--cp-border);
  background: var(--cp-surface-soft);
  color: var(--cp-text);
  padding: 8px 10px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 12px;
}

.chip:hover {
  border-color: rgba(var(--cp-accent-rgb), 0.45);
  background: rgba(var(--cp-accent-rgb), 0.12);
}

.msg-row {
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 12px;
  margin: 14px 0;
}

.msg-row.user {
  direction: rtl;
}

.msg-row.user>* {
  direction: ltr;
}

.avatar {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 6px;
}

.avatar-inner {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  background: var(--cp-surface-soft);
  border: 1px solid var(--cp-border);
}

.avatar-image {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid var(--cp-border);
  background: var(--cp-surface-soft);
}

.msg-row.user .avatar-inner {
  background: rgba(var(--cp-accent-rgb), 0.18);
  border-color: rgba(var(--cp-accent-rgb), 0.32);
}

.bubble {
  border-radius: 16px;
  padding: 12px 12px 10px;
  background: color-mix(in srgb, var(--cp-surface) 88%, transparent);
  border: 1px solid var(--cp-border);
  backdrop-filter: blur(10px);
}

.msg-row.user .bubble {
  background: rgba(var(--cp-accent-rgb), 0.1);
  border-color: rgba(var(--cp-accent-rgb), 0.28);
}

.meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.role {
  font-size: 12px;
  font-weight: 650;
  color: var(--cp-text);
}

.time {
  font-size: 11px;
  color: var(--cp-muted);
}

.content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--cp-text);
  word-break: break-word;
}

.usertext {
  white-space: pre-wrap;
  margin: 0;
  font-family: inherit;
}

.markdown :deep(pre) {
  overflow: auto;
  border-radius: 12px;
  padding: 12px;
  border: 1px solid var(--cp-border);
  background: var(--cp-surface-soft);
}

.markdown :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 13px;
}

.markdown :deep(.hljs) {
  color: var(--cp-text);
  background: var(--cp-surface-soft);
}

.markdown :deep(a) {
  color: var(--cp-accent);
  text-decoration: none;
}

.markdown :deep(a:hover) {
  text-decoration: underline;
}

.markdown :deep(.evidence-inline-btn) {
  display: inline-flex;
  align-items: center;
  margin: 0 2px;
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid rgba(var(--cp-accent-rgb), 0.34);
  background: rgba(var(--cp-accent-rgb), 0.14);
  color: var(--cp-accent);
  font: inherit;
  line-height: 1.5;
  cursor: pointer;
}

.markdown :deep(.evidence-inline-btn:hover) {
  background: rgba(var(--cp-accent-rgb), 0.22);
}

.markdown :deep(p) {
  margin: 0 0 10px;
}

.citations {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--cp-border);
}

.cit-toggle {
  width: 100%;
  margin-bottom: 8px;
  padding: 0;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
}

.cit-title,
.cit-toggle-icon {
  font-size: 12px;
  color: var(--cp-muted);
}

.cit-toggle:hover .cit-title,
.cit-toggle:hover .cit-toggle-icon {
  color: var(--cp-text);
}

.cit-list {
  display: grid;
  gap: 8px;
}

.cit {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 12px;
  background: var(--cp-surface-soft);
  border: 1px solid var(--cp-border);
  width: 100%;
  text-align: left;
  cursor: pointer;
  color: inherit;
}

.cit:hover {
  border-color: rgba(var(--cp-accent-rgb), 0.42);
  background: rgba(var(--cp-accent-rgb), 0.1);
}

.badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  border: 1px solid rgba(var(--cp-accent-rgb), 0.28);
  background: rgba(var(--cp-accent-rgb), 0.14);
  color: var(--cp-text);
  margin-right: 8px;
}

.cit-src {
  font-size: 12px;
  color: var(--cp-text);
}

.cit-meta {
  margin-left: 8px;
  font-size: 12px;
  color: var(--cp-muted);
}

.score {
  font-size: 12px;
  color: var(--cp-muted);
}

.cit-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}

.cit-link {
  font-size: 12px;
  color: var(--cp-accent);
}

.evidence-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.evidence-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.evidence-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.evidence-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cp-text);
}

.evidence-score {
  font-size: 13px;
  color: var(--cp-muted);
}

.evidence-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.evidence-meta-card {
  border: 1px solid var(--cp-border);
  border-radius: 14px;
  background: var(--cp-surface-soft);
  padding: 12px 14px;
}

.evidence-meta-card-wide {
  grid-column: 1 / -1;
}

.evidence-meta-label {
  font-size: 12px;
  color: var(--cp-muted);
}

.evidence-meta-value {
  margin-top: 6px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--cp-text);
  word-break: break-word;
}

.evidence-block {
  border: 1px solid var(--cp-border);
  border-radius: 16px;
  background: color-mix(in srgb, var(--cp-surface) 94%, transparent);
  padding: 14px;
}

.evidence-block-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--cp-text);
}

.evidence-body,
.evidence-empty {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  color: var(--cp-text);
}

.evidence-empty {
  color: var(--cp-muted);
}

:deep(.evidence-modal-wrap .ant-modal-body) {
  max-height: calc(100vh - 180px);
  overflow: auto;
}

.thinking {
  margin-top: 10px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--cp-muted);
}

.spinner {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  border: 2px solid color-mix(in srgb, var(--cp-border) 70%, transparent);
  border-top-color: rgba(var(--cp-accent-rgb), 0.9);
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.composer {
  padding: 14px 0 10px;
  background: transparent;
}

.composer-inner {
  width: min(980px, calc(100% - 32px));
  margin: 0 auto;
}

.composer-panel {
  border: 1px solid var(--cp-border);
  background: color-mix(in srgb, var(--cp-surface) 94%, transparent);
  border-radius: 30px;
  padding: 14px 16px 10px;
  box-shadow: var(--shadow);
}

.textarea {
  width: 100%;
  resize: none;
  border: none;
  background: transparent;
  color: var(--cp-text);
  padding: 0 2px;
  outline: none;
  font-size: 16px;
  line-height: 1.5;
  min-height: 42px;
  max-height: 180px;
}

.composer-tools {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tools-left,
.tools-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-btn {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid var(--cp-border);
  background: var(--cp-surface-soft);
  color: var(--cp-muted);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.tool-btn:hover {
  color: var(--cp-text);
}

.tool-btn.active {
  border-color: rgba(var(--cp-accent-rgb), 0.55);
  background: rgba(var(--cp-accent-rgb), 0.16);
  color: var(--cp-accent);
}

.settings-panel {
  width: 280px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settings-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--cp-text);
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.settings-label {
  font-size: 13px;
  color: var(--cp-text);
}

.settings-control {
  width: 132px;
  flex: 0 0 132px;
}

:deep(.chat-settings-popover .ant-popover-inner) {
  border-radius: 16px;
  background: color-mix(in srgb, var(--cp-surface) 96%, transparent);
  border: 1px solid var(--cp-border);
  box-shadow: var(--shadow);
}

:deep(.chat-settings-popover .ant-popover-inner-content) {
  padding: 14px;
}

.tool-think {
  height: 34px;
  border-radius: 999px;
  border: 1px solid var(--cp-border);
  background: var(--cp-surface-soft);
  color: var(--cp-muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  font-size: 14px;
  cursor: pointer;
}

.tool-think.active {
  border-color: rgba(var(--cp-accent-rgb), 0.55);
  background: rgba(var(--cp-accent-rgb), 0.22);
  color: var(--cp-accent);
}

.tool-think:disabled,
.send-fab:disabled,
.tool-btn:disabled,
.textarea:disabled {
  cursor: not-allowed;
}

.send-fab {
  width: 38px;
  height: 38px;
  border-radius: 999px;
  border: none;
  background: var(--cp-text);
  color: var(--cp-surface);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.send-fab:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.send-icon {
  font-size: 18px;
  line-height: 1;
}

@media (max-width: 720px) {
  .cit {
    align-items: flex-start;
    flex-direction: column;
  }

  .cit-right,
  .evidence-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .evidence-grid {
    grid-template-columns: 1fr;
  }
}
</style>
