import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from './storefront'
import './styles.css'

createRoot(document.getElementById('root')!).render(<BrowserRouter><App/></BrowserRouter>)
