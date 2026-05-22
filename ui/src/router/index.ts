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
      path: '/samples',
      name: 'samples',
      component: () => import('../views/SamplesView.vue'),
    },
    {
      path: '/samples/:id',
      name: 'sample-detail',
      component: () => import('../views/SampleDetailView.vue'),
    },
  ],
})

export default router
