import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/contributors',
      name: 'contributors',
      component: () => import('../views/ContributorsView.vue'),
    },
    {
      path: '/contributors/:id',
      name: 'contributor-detail',
      component: () => import('../views/ContributorDetailView.vue'),
    },
    {
      path: '/playlists',
      name: 'playlists',
      component: () => import('../views/PlaylistsView.vue'),
    },
    {
      path: '/playlists/:id',
      name: 'playlist-detail',
      component: () => import('../views/PlaylistDetailView.vue'),
    },
    {
      path: '/songs',
      name: 'songs',
      component: () => import('../views/SongsView.vue'),
    },
    {
      path: '/songs/:id',
      name: 'song-detail',
      component: () => import('../views/SongDetailView.vue'),
    },
    {
      path: '/guessing',
      name: 'guessing',
      component: () => import('../views/GuessingView.vue'),
    },
    {
      path: '/stats',
      name: 'stats',
      component: () => import('../views/StatsView.vue'),
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('../views/AdminView.vue'),
      meta: { requiresAdmin: true },
    },
  ],
})

// Cosmetic only — the real gate is the Lambda API's `admin` AuthMode (403 without the Cognito
// `admins` group). A signed-in non-admin is bounced home; an unauthenticated deep link is let
// through because the login overlay covers the page until there is a session to judge, and
// AdminView itself renders a "forbidden" state if that session turns out not to be an admin.
router.beforeEach((to) => {
  if (!to.meta.requiresAdmin) return true
  const authStore = useAuthStore()
  return authStore.isAuthenticated && !authStore.isAdmin ? { name: 'home' } : true
})

export default router
