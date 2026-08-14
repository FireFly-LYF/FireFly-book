import axios, { type AxiosInstance, type AxiosError } from 'axios'

// ---------------------------------------------------------------------------
// 与后端 gateway/internal/handler 保持一致的响应结构：{ code, msg, data }
// ---------------------------------------------------------------------------

/** 网关统一 API 响应外壳 */
export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

/** 登录成功后返回的 data 字段 */
export interface LoginData {
  token: string
}

/** 下游节点，对应 registry.Node */
export interface ServiceNode {
  id: string
  http?: string
  grpc?: string
  tcp?: string
  weight: number
  healthy: boolean
  last_check: string
}

/** 注册下游请求体，http/grpc/tcp/addr 至少填一项 */
export interface RegisterServiceRequest {
  http?: string
  grpc?: string
  tcp?: string
  addr?: string
  weight?: number
}

/** 下线节点请求体，id 与 http 二选一（http 与 id 等价） */
export interface DeregisterServiceRequest {
  id?: string
  http?: string
}

/** 单日统计 data 字段 */
export interface StatisticData {
  tenant: string
  date: string
  count: number
}

/** 7 日统计中每一天的记录 */
export interface DayCount {
  date: string
  count: number
}

/** 7 日统计 report 的 data 字段 */
export interface StatisticsReportData {
  tenant: string
  days: DayCount[]
}

// ---------------------------------------------------------------------------
// localStorage 键名（登录后写入，路由守卫与拦截器读取）
// ---------------------------------------------------------------------------

export const TOKEN_KEY = 'gateway_token'
export const TENANT_KEY = 'gateway_tenant'

/** 读取已保存的 JWT */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 保存登录态 */
export function saveAuth(token: string, tenant: string): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(TENANT_KEY, tenant)
}

/** 清除登录态 */
export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TENANT_KEY)
}

/** 读取当前登录租户名（Dashboard 查统计用） */
export function getTenant(): string | null {
  return localStorage.getItem(TENANT_KEY)
}

// ---------------------------------------------------------------------------
// axios 实例：开发时 Vite 代理 /gateway → localhost:8080
// ---------------------------------------------------------------------------

const http: AxiosInstance = axios.create({
  baseURL: '/gateway',
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截：自动附加 Bearer Token（访问 /api/* 或后续鉴权管理接口时使用）
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：解包 { code, msg, data }，非 0 视为业务错误
http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code !== 0) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const msg = error.response?.data?.msg ?? error.message ?? '网络错误'
    return Promise.reject(new Error(msg))
  },
)

/** 从响应中取出 data 字段 */
function unwrap<T>(response: { data: ApiResponse<T> }): T {
  return response.data.data
}

// ---------------------------------------------------------------------------
// /gateway/* 管理 API 封装
// ---------------------------------------------------------------------------

/** POST /gateway/login — 运维口令登录，签发 typ=admin JWT（非业务 Access） */
export async function login(tenant: string, password: string): Promise<LoginData> {
  const res = await http.post<ApiResponse<LoginData>>('/login', { tenant, password })
  return unwrap(res)
}

/** GET /gateway/health — 网关存活探测 */
export async function getHealth(): Promise<string> {
  const res = await http.get<ApiResponse<string>>('/health')
  return unwrap(res)
}

/** GET /gateway/services — 列出所有下游节点 */
export async function listServices(): Promise<ServiceNode[]> {
  const res = await http.get<ApiResponse<ServiceNode[]>>('/services')
  return unwrap(res)
}

/** POST /gateway/services — 注册新下游 */
export async function registerService(body: RegisterServiceRequest): Promise<{ id: string }> {
  const res = await http.post<ApiResponse<{ id: string }>>('/services', body)
  return unwrap(res)
}

/** DELETE /gateway/services — 按 id 下线节点 */
export async function deregisterService(body: DeregisterServiceRequest): Promise<void> {
  await http.delete('/services', { data: body })
}

/** GET /gateway/statistic?tenant=&date= — 查询指定租户某日请求量 */
export async function getStatistic(tenant: string, date?: string): Promise<StatisticData> {
  const res = await http.get<ApiResponse<StatisticData>>('/statistic', {
    params: { tenant, date },
  })
  return unwrap(res)
}

/** GET /gateway/statistics/report?tenant= — 最近 7 日请求量 */
export async function getStatisticsReport(tenant: string): Promise<StatisticsReportData> {
  const res = await http.get<ApiResponse<StatisticsReportData>>('/statistics/report', {
    params: { tenant },
  })
  return unwrap(res)
}
