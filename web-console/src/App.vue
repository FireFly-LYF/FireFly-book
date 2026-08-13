<script setup>
import { computed, ref } from 'vue'
import BottomNav from './components/BottomNav.vue'
import LoginSheet from './components/LoginSheet.vue'
import NoteDetail from './components/NoteDetail.vue'
import TopBar from './components/TopBar.vue'
import HomeView from './views/HomeView.vue'
import MarketView from './views/MarketView.vue'
import MessageView from './views/MessageView.vue'
import ProfileView from './views/ProfileView.vue'
import PublishView from './views/PublishView.vue'
import SearchView from './views/SearchView.vue'
import { loadAuthSession, saveAuthSession, normalizeAuthPayload, userApi } from './api'


const tab = ref('home')
const showSearch = ref(false)
const session = ref(loadAuthSession())
const loginOpen = ref(false)
const detailId = ref(null)
const viewingUserId = ref(null)
const unread = ref(0)
const toast = ref('')
let toastTimer = null

const userId = computed(() => session.value?.user?.id ?? null)
const loggedIn = computed(() => {
  const s = session.value
  return !!(s?.user && (s.token || s.accessToken))
})
const displayName = computed(
  () => session.value?.user?.nickname || session.value?.user?.username || '',
)

function saveSession(auth) {
  const next = auth ? normalizeAuthPayload(auth) : null
  session.value = next
  saveAuthSession(next)
}

function showToast(msg) {
  toast.value = msg
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value = ''
  }, 2200)
}

function needLogin() {
  loginOpen.value = true
}

function onLoginSuccess(data) {
  saveSession(data)
  loginOpen.value = false
  showToast('登录成功')
}

async function logout() {
  try {
    if (session.value?.token || session.value?.accessToken) {
      await userApi().logout()
    }
  } catch {
    /* 忽略网络错误，本地仍清会话 */
  }
  saveSession(null)
  unread.value = 0
  viewingUserId.value = null
  showToast('已退出')
}

function onNav(id) {
  showSearch.value = false
  if (id === 'me') viewingUserId.value = null
  tab.value = id
}

function openNote(id) {
  detailId.value = id
}

function openProfile(uid) {
  if (!uid) return
  const id = Number(uid)
  if (!id) return
  showSearch.value = false
  detailId.value = null
  viewingUserId.value = id
  tab.value = 'me'
}

function backFromProfile() {
  viewingUserId.value = null
}

function onPublished(note) {
  viewingUserId.value = null
  tab.value = 'me'
  const id = note?.id ?? note?.note?.id
  if (id) detailId.value = id
}

function setUnread(n) {
  unread.value = Number(n) || 0
}

function onProfileUpdated(user) {
  if (!(session.value?.token || session.value?.accessToken) || !user) return
  saveSession({
    token: session.value.token || session.value.accessToken,
    accessToken: session.value.accessToken || session.value.token,
    refreshToken: session.value.refreshToken,
    user,
  })
}
</script>

<template>
  <div class="app">
    <TopBar
      class="desktop-only"
      :active="tab"
      :unread="unread"
      :logged-in="loggedIn"
      :display-name="displayName"
      :avatar-url="session?.user?.avatarUrl || ''"
      :user-id="userId"
      @change="onNav"
      @search="showSearch = true"
      @login="needLogin"
      @logout="logout"
    />

    <div class="shell">
      <main class="main">
        <SearchView
          v-if="showSearch"
          :logged-in="loggedIn"
          @back="showSearch = false"
          @open-note="openNote"
          @open-profile="openProfile"
          @need-login="needLogin"
          @toast="showToast"
        />
        <HomeView
          v-else-if="tab === 'home'"
          :user-id="userId"
          :logged-in="loggedIn"
          @open-note="openNote"
          @open-profile="openProfile"
          @need-login="needLogin"
          @search="showSearch = true"
        />
        <MarketView v-else-if="tab === 'market'" />
        <PublishView
          v-else-if="tab === 'publish'"
          :logged-in="loggedIn"
          @need-login="needLogin"
          @toast="showToast"
          @published="onPublished"
        />
        <MessageView
          v-else-if="tab === 'msg'"
          :logged-in="loggedIn"
          :user-id="userId"
          @need-login="needLogin"
          @toast="showToast"
          @unread="setUnread"
          @open-profile="openProfile"
        />
        <ProfileView
          v-else
          :session="session"
          :profile-user-id="viewingUserId"
          @need-login="needLogin"
          @logout="logout"
          @open-note="openNote"
          @open-profile="openProfile"
          @back="backFromProfile"
          @toast="showToast"
          @profile-updated="onProfileUpdated"
        />
      </main>
    </div>

    <BottomNav
      class="mobile-only"
      v-show="!showSearch && !detailId"
      :active="tab"
      :unread="unread"
      @change="onNav"
    />

    <NoteDetail
      :open="!!detailId"
      :note-id="detailId"
      :logged-in="loggedIn"
      @close="detailId = null"
      @need-login="needLogin"
      @toast="showToast"
      @open-profile="openProfile"
    />

    <LoginSheet
      :open="loginOpen"
      @close="loginOpen = false"
      @success="onLoginSuccess"
    />

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

.shell {
  flex: 1;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 1rem 2rem;
}

.main {
  min-height: calc(100vh - 64px);
  position: relative;
}

.toast {
  position: fixed;
  left: 50%;
  bottom: 2.5rem;
  transform: translateX(-50%);
  z-index: 60;
  padding: 0.6rem 1.1rem;
  border-radius: 18px;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  font-size: 0.86rem;
  max-width: min(90vw, 420px);
  text-align: center;
  pointer-events: none;
}

.mobile-only { display: none; }
.desktop-only { display: block; }

@media (max-width: 768px) {
  .desktop-only { display: none !important; }
  .mobile-only { display: grid !important; }
  .shell {
    padding: 0 0 calc(64px + var(--safe-bottom));
    max-width: none;
  }
  .main { min-height: 100dvh; }
  .toast { bottom: 5.5rem; }
}
</style>
