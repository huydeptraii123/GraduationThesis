import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
// Phong cách chung của mọi biểu đồ — nạp một lần ở đây để không biểu đồ nào lọt ra ngoài.
import './components/charts/charts.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
