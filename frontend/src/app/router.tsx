import { createBrowserRouter } from 'react-router-dom'
import { LoginPage } from '../features/auth/LoginPage'
import { BomPage } from '../features/bom/BomPage'
import { CuttingPlanApprovalPage } from '../features/cutting-plans/CuttingPlanApprovalPage'
import { CuttingPlanDetailPage } from '../features/cutting-plans/CuttingPlanDetailPage'
import { CuttingPlanListPage } from '../features/cutting-plans/CuttingPlanListPage'
import { CuttingPlanShortagesPage } from '../features/cutting-plans/CuttingPlanShortagesPage'
import { InventoryPage } from '../features/inventory/InventoryPage'
import { SalesOrderPage } from '../features/sales-orders/SalesOrderPage'
import { UsersPage } from '../features/users/UsersPage'
import { AppLayout } from './AppLayout'
import { HomePage } from './HomePage'
import { RequireAuth } from './RequireAuth'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: 'inventory', element: <InventoryPage /> },
      { path: 'bom', element: <BomPage /> },
      { path: 'sales-orders', element: <SalesOrderPage /> },
      { path: 'cutting-plans', element: <CuttingPlanListPage /> },
      { path: 'cutting-plans/approval', element: <CuttingPlanApprovalPage /> },
      { path: 'cutting-plans/:id', element: <CuttingPlanDetailPage /> },
      { path: 'cutting-plans/:id/shortages', element: <CuttingPlanShortagesPage /> },
      { path: 'users', element: <UsersPage /> },
    ],
  },
])
