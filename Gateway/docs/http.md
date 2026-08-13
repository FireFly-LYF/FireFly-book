*查找后台端口占用*
netstat -ano | findstr :8080

*http状态码*
状态码	 含义	        一句话解释	                            最常见场景
200	    成功	        请求一切正常	                    GET/PUT 请求成功返回数据
400	    请求错误	    你发的东西格式不对或少了参数	    JSON 解析失败、必填字段为空
401	    未认证	        你没登录或 token 过期了	        接口需要登录，但请求头没带 Token
403	    禁止访问	    登录了但没权限	                    普通用户调用管理员接口
429     过多请求        短时间内向服务器发送了太多请求，超过了频率限制
404	    路径不存在	    URL 写错了或资源被删了	          /api/user/123 这个用户不存在
500	    服务器内部错误	后端代码报异常                  	任何未捕获的运行时异常
502     错误的网关	    网关（Nginx）与后端（应用）之间通信出问题
503 	服务不可用	    服务器过载、维护中、流量激增等             过载或维护

---

*JWT 流程（双令牌）*
1. 登录/注册：user-service 返回 accessToken（短 JWT，typ=access）+ refreshToken（随机串，库内只存 SHA-256）
2. 日常请求：Authorization: Bearer <accessToken> → 网关验签 → 注入 X-User-Id
3. Access 过期（网关 401）：前端 POST /api/user/refresh（白名单）带 refreshToken + deviceFingerprint → 校验指纹一致并轮换 → 返回新双令牌
4. 登出：POST /api/user/logout 撤销该用户全部 refresh
注意：refresh 不能当 Bearer；网关拒绝 typ≠access 的 JWT；换设备/清站点数据导致指纹变 → 必须重新登录

---

*限流算法*
1、 固定窗口
 把时间切成固定段（如每 1 秒），每段一个计数器。
 边界突发示例（limit=10）：
第 0.9 秒：10 次 ✅
第 1.1 秒：10 次 ✅
→ 0.9s～1.1s 这 0.2 秒内实际 20 次，但算法认为「合法」

2、滑动窗口
不看「整秒」，而看「过去 N 秒内」一共多少请求。
实现复杂，Redis 存储/计算更多

3、令牌桶
桶里放令牌，按固定速率补充；请求来就「消耗 1 个令牌」，没有令牌就拒绝。
用Redis实现时，必须用Lua脚本将“取令牌”和“扣减令牌”两个操作原子化，否则并发下会多放行请求。

*进程*
操作系统里一个独立运行的程序实例，有自己独立的内存空间。
进程 A：gateway（:8080）
进程 B：downstream :9001
进程 C：downstream :9002
进程 D：Redis

特点	        说明
内存        各自独立，A 不能直接读 B 的变量
崩溃        A 挂了，B 一般不受影响
创建成本    高（要分配整套资源）
通信        socket、Redis、HTTP 等

*线程*
进程内部的执行单元，同一进程里的线程 共享内存。
线程在等待时被阻塞，白白占用CPU和内存	

进程：gateway
  ├── 线程 1：处理 HTTP 请求 A
  ├── 线程 2：处理 HTTP 请求 B
  └── 线程 3：健康检查（若由 OS 线程跑）
特点	        说明
内存        同进程内共享
创建成本    比进程低，但仍不算轻
调度        由操作系统调度
问题        多线程同时改同一变量 → 需要锁

*协程*
用户态的轻量「并发任务」；Go: goroutine。
协程在等待时主动让出，去处理其他就绪任务，CPU利用率拉满

操作系统
└── 进程 gateway（独立内存）
      │
      ├── OS 线程 1 ─┐
      ├── OS 线程 2 ─┼─ Go runtime 调度
      └── OS 线程 N ─┘
            │
            ├── goroutine: main
            ├── goroutine: 健康检查
            ├── goroutine: 处理 /api/user
            └── goroutine: 处理 /api/order
                  │
                  └── 都要访问 reg.nodes
                        │
                        └── sync.RWMutex 保护

*锁（Lock）*
同一时刻只允许一个人改共享数据，防止竞态（race condition）
保护「同一进程内、被多个 goroutine 共享的数据」


*保护对比*
机制	       保护谁	      触发条件	            目的
限流        网关资源     QPS / 日配额超限       防止请求太多把网关打满
健康检查    路由选择     定时探测 /health       预防把流量打到已知坏节点
熔断        网关+下游    连续转发失败（5xx）     快速失败，防止雪崩

*header重写*
1、客户端带 Authorization: Bearer <JWT>
2、JWT 中间件解析后写入 c.Set("tenant", ...)
3、requestWithProxyContext 从 Gin 上下文读到 request.Context
4、applyProxyRewrite 从 Context 读出，才设置 X-Tenant-Id