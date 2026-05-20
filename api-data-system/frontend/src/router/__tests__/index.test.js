import { describe, it, expect, vi } from 'vitest'

// Mock all component imports
vi.mock('../../views/Dashboard.vue', () => ({ default: { template: '<div>Dashboard</div>' } }))
vi.mock('../../views/TaskScheduler.vue', () => ({ default: { template: '<div>TaskScheduler</div>' } }))
vi.mock('../../views/TokenManagement.vue', () => ({ default: { template: '<div>TokenManagement</div>' } }))
vi.mock('../../views/ApiManagement.vue', () => ({ default: { template: '<div>ApiManagement</div>' } }))
vi.mock('../../views/FieldMapping.vue', () => ({ default: { template: '<div>FieldMapping</div>' } }))
vi.mock('../../views/Logs.vue', () => ({ default: { template: '<div>Logs</div>' } }))

import router from '../index'

describe('Router', () => {
  it('should have all routes defined', () => {
    const routes = router.getRoutes()
    const routeNames = routes.map(r => r.name)

    expect(routeNames).toContain('Dashboard')
    expect(routeNames).toContain('TaskScheduler')
    expect(routeNames).toContain('TokenManagement')
    expect(routeNames).toContain('ApiManagement')
    expect(routeNames).toContain('FieldMapping')
    expect(routeNames).toContain('Logs')
  })

  it('should redirect root to /dashboard', () => {
    const routes = router.getRoutes()
    const rootRoute = routes.find(r => r.path === '/')
    expect(rootRoute).toBeDefined()
    expect(rootRoute.redirect).toBe('/dashboard')
  })

  it('should have /dashboard route', () => {
    const routes = router.getRoutes()
    const dashRoute = routes.find(r => r.path === '/dashboard')
    expect(dashRoute).toBeDefined()
    expect(dashRoute.name).toBe('Dashboard')
  })

  it('should have /task-scheduler route', () => {
    const routes = router.getRoutes()
    const route = routes.find(r => r.path === '/task-scheduler')
    expect(route).toBeDefined()
  })

  it('should have /token-management route', () => {
    const routes = router.getRoutes()
    const route = routes.find(r => r.path === '/token-management')
    expect(route).toBeDefined()
  })

  it('should have /api-management route', () => {
    const routes = router.getRoutes()
    const route = routes.find(r => r.path === '/api-management')
    expect(route).toBeDefined()
  })

  it('should have /field-mapping route', () => {
    const routes = router.getRoutes()
    const route = routes.find(r => r.path === '/field-mapping')
    expect(route).toBeDefined()
  })

  it('should have /logs route', () => {
    const routes = router.getRoutes()
    const route = routes.find(r => r.path === '/logs')
    expect(route).toBeDefined()
  })
})
