import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

// 创建 Vue 应用，挂载路由与 Element Plus 组件库
const app = createApp(App)

app.use(router)
app.use(ElementPlus)

app.mount('#app')
