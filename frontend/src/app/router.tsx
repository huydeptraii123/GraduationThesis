import { createBrowserRouter } from 'react-router-dom'
import { LoginPage } from '../features/auth/LoginPage'
import { BomPage } from '../features/bom/BomPage'
import { CuttingPlanDetailPage } from '../features/cutting-plans/CuttingPlanDetailPage'
import { CuttingPlanListPage } from '../features/cutting-plans/CuttingPlanListPage'
import { InventoryPage } from '../features/inventory/InventoryPage'
import { SalesOrderPage } from '../features/sales-orders/SalesOrderPage'
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
      { path: 'cutting-plans/:id', element: <CuttingPlanDetailPage /> },
    ],
  },
])
