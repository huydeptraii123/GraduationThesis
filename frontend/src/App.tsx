import { App as AntdApp, ConfigProvider } from 'antd'
import viVN from 'antd/locale/vi_VN'
import { RouterProvider } from 'react-router-dom'
import { router } from './app/router'
import { APP_THEME } from './app/theme'
import { AuthProvider } from './features/auth/AuthContext'

function App() {
  return (
    <ConfigProvider locale={viVN} theme={APP_THEME}>
      <AntdApp>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </AntdApp>
    </ConfigProvider>
  )
}

export default App
