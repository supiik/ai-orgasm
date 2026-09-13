import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

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

// No redirect on purpose: AdminView renders a "you are not an administrator" explanation for a
// signed-in non-admin, which is more useful than a silent bounce home (the usual cause is simply
// that the account hasn't been added to the Cognito `admins` group yet, or hasn't re-signed-in
// since). The real gate is the Lambda API's `admin` AuthMode (403 without the group); `meta.
// requiresAdmin` is kept so a guard can be added if a route ever needs one.

export default router
