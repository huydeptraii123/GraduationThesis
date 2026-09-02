import { ConfigProvider, Layout, Typography } from 'antd'

function App() {
  return (
    <ConfigProvider>
      <Layout style={{ minHeight: '100vh' }}>
        <Layout.Content style={{ padding: 24 }}>
          <Typography.Title level={3}>Cutting Stock Optimization</Typography.Title>
        </Layout.Content>
      </Layout>
    </ConfigProvider>
  )
}

export default App
