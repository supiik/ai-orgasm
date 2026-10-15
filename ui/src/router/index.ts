import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { setPendingInviteToken } from '@/lib/pendingInvite'

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
    {
      path: '/spotify/callback',
      name: 'spotify-callback',
      component: () => import('../views/SpotifyCallbackView.vue'),
    },
    {
      path: '/organization',
      name: 'organization',
      component: () => import('../views/OrganizationSettingsView.vue'),
      meta: { requiresOrgAdmin: true },
    },
    {
      // Org-join invite link (e.g. /join?token=xxx). `beforeEnter` runs even while a login
      // overlay gates <RouterView> off (navigation guards resolve independently of what's
      // currently rendered) — stashes the token for MockLoginOverlay/CognitoLoginOverlay to pick
      // up, then redirects home. There is deliberately no rendered JoinView: by the time this
      // guard would let a component through (the caller already signed in and linked), there's
      // nothing left for a dedicated page to do.
      path: '/join',
      name: 'join',
      component: HomeView,
      beforeEnter: (to) => {
        const token = to.query.token
        if (typeof token === 'string' && token) setPendingInviteToken(token)
        return { path: '/' }
      },
    },
  ],
})

// No redirect on purpose: AdminView renders a "you are not an administrator" explanation for a
// signed-in non-admin, which is more useful than a silent bounce home (the usual cause is simply
// that the account hasn't been added to the Cognito `admins` group yet, or hasn't re-signed-in
// since). The real gate is the Lambda API's `admin` AuthMode (403 without the group); `meta.
// requiresAdmin` is kept so a guard can be added if a route ever needs one.

export default router
