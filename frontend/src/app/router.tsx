import { createBrowserRouter } from 'react-router-dom'
import { LoginPage } from '../features/auth/LoginPage'
import { BomPage } from '../features/bom/BomPage'
import { InventoryPage } from '../features/inventory/InventoryPage'
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
    ],
  },
])
