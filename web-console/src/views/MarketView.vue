<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import SignedImg from '../components/SignedImg.vue'
import {
  marketApi,
  mediaApi,
  newIdempotencyKey,
  toLocalMediaUrl,
} from '../api'

const props = defineProps({
  loggedIn: Boolean,
  userId: { type: [Number, null], default: null },
})
const emit = defineEmits(['need-login', 'toast', 'open-profile'])

/** browse | cart | orders | sell */
const tab = ref('browse')
const loading = ref(false)
const busy = ref(false)

const products = ref([])
const detail = ref(null)
const cart = ref([])
const addresses = ref([])
const buyerOrders = ref([])
const sellerOrders = ref([])
const sellerProducts = ref([])

const orderRole = ref('buyer') // buyer | seller
const checkoutOpen = ref(false)
const checkoutFromCart = ref(false)
const checkoutItems = ref([])
const selectedAddressId = ref(null)
const addressFormOpen = ref(false)
const addressForm = ref(emptyAddress())
const sellForm = ref(emptySell())
const sellAccessUrl = ref('')
const sellIdemKey = ref(null)
const buyQty = ref(1)

const STATUS_LABEL = {
  CREATED: '待付款',
  PAID: '待发货',
  SHIPPED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '售后中',
  REFUNDED: '已退款',
}

watch(
  () => [props.loggedIn, tab.value],
  () => {
    if (tab.value === 'browse') loadProducts()
    else if (tab.value === 'cart') loadCart()
    else if (tab.value === 'orders') loadOrders()
    else if (tab.value === 'sell') loadSeller()
  },
)

onMounted(() => {
  loadProducts()
  if (props.loggedIn) loadCart()
})

watch(
  () => props.loggedIn,
  (v) => {
    if (v) loadCart()
    else cart.value = []
  },
)

function emptyAddress() {
  return {
    receiverName: '',
    phone: '',
    province: '',
    city: '',
    district: '',
    detail: '',
    isDefault: true,
  }
}

function emptySell() {
  return {
    title: '',
    description: '',
    priceYuan: '',
    stock: '10',
    coverUrl: '',
  }
}

function yuan(cents) {
  if (cents == null || Number.isNaN(Number(cents))) return '—'
  return (Number(cents) / 100).toFixed(2)
}

function statusClass(status) {
  const map = {
    CREATED: 'st-created',
    PAID: 'st-active',
    SHIPPED: 'st-active',
    COMPLETED: 'st-done',
    CANCELLED: 'st-muted',
    REFUNDED: 'st-muted',
    REFUNDING: 'st-warn',
  }
  return map[status] || 'st-muted'
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}

function parseAddressObj(snapshot) {
  if (!snapshot) return null
  try {
    return typeof snapshot === 'string' ? JSON.parse(snapshot) : snapshot
  } catch {
    return null
  }
}

function addressContact(snapshot) {
  const a = parseAddressObj(snapshot)
  if (!a) return snapshot ? String(snapshot) : ''
  return [a.receiverName, a.phone].filter(Boolean).join(' ')
}

function addressLine(snapshot) {
  const a = parseAddressObj(snapshot)
  if (!a) return ''
  return `${a.province || ''}${a.city || ''}${a.district || ''}${a.detail || ''}`
}

function requireLogin() {
  if (!props.loggedIn) {
    emit('need-login')
    return false
  }
  return true
}

async function loadProducts() {
  loading.value = true
  try {
    const res = await marketApi().products(1, 40)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '加载商品失败')
      products.value = []
      return
    }
    products.value = res.body.data || []
  } finally {
    loading.value = false
  }
}

async function openDetail(id) {
  loading.value = true
  buyQty.value = 1
  try {
    const res = await marketApi().product(id)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '商品不存在')
      return
    }
    detail.value = res.body.data
  } finally {
    loading.value = false
  }
}

async function loadCart() {
  if (!requireLogin()) return
  loading.value = true
  try {
    const res = await marketApi().cart()
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '购物车加载失败')
      return
    }
    cart.value = res.body.data || []
  } finally {
    loading.value = false
  }
}

async function loadAddresses() {
  if (!requireLogin()) return
  const res = await marketApi().addresses()
  if (res.body?.code !== 0) {
    emit('toast', res.body?.message || '地址加载失败')
    addresses.value = []
    return
  }
  addresses.value = res.body.data || []
  const def = addresses.value.find((a) => a.isDefault === 1) || addresses.value[0]
  selectedAddressId.value = def?.id ?? null
}

async function loadOrders() {
  if (!requireLogin()) return
  loading.value = true
  try {
    if (orderRole.value === 'buyer') {
      const res = await marketApi().orders(1, 40)
      if (res.body?.code !== 0) {
        emit('toast', res.body?.message || '订单加载失败')
        buyerOrders.value = []
        return
      }
      buyerOrders.value = res.body.data || []
    } else {
      const res = await marketApi().sellerOrders(1, 40)
      if (res.body?.code !== 0) {
        emit('toast', res.body?.message || '卖家订单加载失败')
        sellerOrders.value = []
        return
      }
      sellerOrders.value = res.body.data || []
    }
  } finally {
    loading.value = false
  }
}

async function loadSeller() {
  if (!requireLogin()) return
  loading.value = true
  try {
    const res = await marketApi().sellerProducts(1, 40)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '我的商品加载失败')
      sellerProducts.value = []
      return
    }
    sellerProducts.value = res.body.data || []
  } finally {
    loading.value = false
  }
}

async function addToCart() {
  if (!requireLogin() || !detail.value) return
  busy.value = true
  try {
    const res = await marketApi().cartAdd({
      productId: detail.value.id,
      quantity: buyQty.value,
    })
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '加入失败')
      return
    }
    cart.value = res.body.data || []
    emit('toast', '已加入购物车')
  } finally {
    busy.value = false
  }
}

async function buyNow() {
  if (!requireLogin() || !detail.value) return
  checkoutFromCart.value = false
  checkoutItems.value = [
    {
      productId: detail.value.id,
      quantity: buyQty.value,
      title: detail.value.title,
      priceCents: detail.value.priceCents,
      coverUrl: detail.value.coverUrl,
    },
  ]
  await loadAddresses()
  checkoutOpen.value = true
}

async function checkoutCart() {
  if (!requireLogin()) return
  if (!cart.value.length) return emit('toast', '购物车为空')
  const sellers = new Set(cart.value.map((c) => c.sellerId).filter(Boolean))
  if (sellers.size > 1) {
    return emit('toast', '请先结算同一卖家的商品')
  }
  checkoutFromCart.value = true
  checkoutItems.value = cart.value.map((c) => ({
    productId: c.productId,
    quantity: c.quantity,
    title: c.title,
    priceCents: c.priceCents,
    coverUrl: c.coverUrl,
  }))
  await loadAddresses()
  checkoutOpen.value = true
}

const checkoutTotalCents = computed(() =>
  checkoutItems.value.reduce(
    (sum, i) => sum + Number(i.priceCents || 0) * Number(i.quantity || 0),
    0,
  ),
)

const cartTotalCents = computed(() =>
  cart.value.reduce(
    (sum, i) => sum + Number(i.priceCents || 0) * Number(i.quantity || 0),
    0,
  ),
)

const cartCount = computed(() =>
  cart.value.reduce((n, i) => n + Number(i.quantity || 0), 0),
)

const currentOrders = computed(() =>
  orderRole.value === 'buyer' ? buyerOrders.value : sellerOrders.value,
)

async function submitOrder() {
  if (!selectedAddressId.value) return emit('toast', '请选择收货地址')
  busy.value = true
  try {
    const body = checkoutFromCart.value
      ? { addressId: selectedAddressId.value, fromCart: true }
      : {
          addressId: selectedAddressId.value,
          fromCart: false,
          items: checkoutItems.value.map((i) => ({
            productId: i.productId,
            quantity: i.quantity,
          })),
        }
    const res = await marketApi().createOrder(body, newIdempotencyKey())
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '下单失败')
      return
    }
    checkoutOpen.value = false
    detail.value = null
    emit('toast', '订单已创建')
    tab.value = 'orders'
    orderRole.value = 'buyer'
    await loadOrders()
  } finally {
    busy.value = false
  }
}

async function saveAddress() {
  if (!requireLogin()) return
  const f = addressForm.value
  if (!f.receiverName.trim() || !f.phone.trim() || !f.province.trim() || !f.city.trim() || !f.detail.trim()) {
    return emit('toast', '请填写完整地址')
  }
  busy.value = true
  try {
    const res = await marketApi().createAddress({
      receiverName: f.receiverName.trim(),
      phone: f.phone.trim(),
      province: f.province.trim(),
      city: f.city.trim(),
      district: f.district.trim() || null,
      detail: f.detail.trim(),
      isDefault: !!f.isDefault,
    })
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '保存失败')
      return
    }
    addressFormOpen.value = false
    addressForm.value = emptyAddress()
    emit('toast', '地址已保存')
    await loadAddresses()
  } finally {
    busy.value = false
  }
}

async function onSellCover(e) {
  if (!requireLogin()) return
  const file = e.target.files?.[0]
  if (!file) return
  busy.value = true
  try {
    const res = await mediaApi().upload(file)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '上传失败')
      return
    }
    const data = res.body.data || {}
    sellForm.value.coverUrl = toLocalMediaUrl(data.url) || data.url || ''
    sellAccessUrl.value = data.accessUrl || sellForm.value.coverUrl
    emit('toast', '封面已上传')
  } finally {
    busy.value = false
    e.target.value = ''
  }
}

async function publishProduct() {
  if (!requireLogin()) return
  const f = sellForm.value
  const title = f.title.trim()
  const yuanNum = Number(f.priceYuan)
  const stock = Number(f.stock)
  if (!title) return emit('toast', '填写标题')
  if (!yuanNum || yuanNum <= 0) return emit('toast', '价格需大于 0')
  if (!Number.isInteger(stock) || stock < 0) return emit('toast', '库存不合法')
  busy.value = true
  if (!sellIdemKey.value) sellIdemKey.value = newIdempotencyKey()
  try {
    const res = await marketApi().createProduct(
      {
        title,
        description: f.description.trim() || null,
        priceCents: Math.round(yuanNum * 100),
        stock,
        coverUrl: f.coverUrl || null,
      },
      sellIdemKey.value,
    )
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '上架失败')
      return
    }
    sellIdemKey.value = null
    sellForm.value = emptySell()
    sellAccessUrl.value = ''
    emit('toast', '商品已上架')
    await loadSeller()
    await loadProducts()
  } finally {
    busy.value = false
  }
}

async function toggleProduct(p) {
  if (!requireLogin()) return
  busy.value = true
  try {
    const res =
      p.status === 'ON' ? await marketApi().productOff(p.id) : await marketApi().productOn(p.id)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '操作失败')
      return
    }
    await loadSeller()
    await loadProducts()
  } finally {
    busy.value = false
  }
}

async function updateCartQty(productId, quantity) {
  if (quantity < 1) return
  const res = await marketApi().cartUpdate(productId, quantity)
  if (res.body?.code !== 0) return emit('toast', res.body?.message || '更新失败')
  cart.value = res.body.data || []
}

async function removeCart(productId) {
  const res = await marketApi().cartRemove(productId)
  if (res.body?.code !== 0) return emit('toast', res.body?.message || '删除失败')
  cart.value = res.body.data || []
}

async function actOrder(fn, okMsg) {
  busy.value = true
  try {
    const res = await fn()
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '操作失败')
      return
    }
    if (okMsg) emit('toast', okMsg)
    await loadOrders()
  } finally {
    busy.value = false
  }
}

function switchOrderRole(role) {
  orderRole.value = role
  loadOrders()
}

function openAddressManage() {
  addressFormOpen.value = true
  loadAddresses()
}
</script>

<template>
  <div class="market">
    <header class="m-head">
      <strong>市集</strong>
      <nav class="tabs">
        <button type="button" :class="{ on: tab === 'browse' }" @click="tab = 'browse'">逛逛</button>
        <button type="button" :class="{ on: tab === 'cart' }" @click="tab = 'cart'">
          购物车<span v-if="cartCount" class="badge">{{ cartCount }}</span>
        </button>
        <button type="button" :class="{ on: tab === 'orders' }" @click="tab = 'orders'">订单</button>
        <button type="button" :class="{ on: tab === 'sell' }" @click="tab = 'sell'">卖家</button>
      </nav>
    </header>

    <!-- 逛逛 -->
    <section v-if="tab === 'browse'" class="pane">
      <p v-if="loading" class="tip">加载中…</p>
      <div v-else-if="products.length" class="grid">
        <article
          v-for="p in products"
          :key="p.id"
          class="sku"
          role="button"
          tabindex="0"
          @click="openDetail(p.id)"
          @keydown.enter="openDetail(p.id)"
        >
          <SignedImg v-if="p.coverUrl" class="sku-img" :src="p.coverUrl" alt="" />
          <div v-else class="ph" />
          <h3>{{ p.title }}</h3>
          <p class="price">¥{{ yuan(p.priceCents) }}</p>
        </article>
      </div>
      <div v-else class="empty-box">
        <p>暂无商品</p>
        <button type="button" class="link" @click="tab = 'sell'">去上架一件</button>
      </div>
    </section>

    <!-- 购物车 -->
    <section v-else-if="tab === 'cart'" class="pane pad" :class="{ 'has-bar': loggedIn && cart.length }">
      <p v-if="!loggedIn" class="empty-box">登录后查看购物车</p>
      <template v-else>
        <p v-if="loading" class="tip">加载中…</p>
        <ul v-else-if="cart.length" class="cart-list">
          <li v-for="c in cart" :key="c.productId" class="cart-row">
            <SignedImg v-if="c.coverUrl" class="thumb lg" :src="c.coverUrl" alt="" />
            <div v-else class="thumb lg ph" />
            <div class="cart-meta">
              <strong class="clamp2">{{ c.title || `商品#${c.productId}` }}</strong>
              <div class="cart-foot">
                <span class="price">¥{{ yuan(c.priceCents) }}</span>
                <div class="qty">
                  <button type="button" @click="updateCartQty(c.productId, c.quantity - 1)">−</button>
                  <span>{{ c.quantity }}</span>
                  <button type="button" @click="updateCartQty(c.productId, c.quantity + 1)">+</button>
                </div>
              </div>
              <button type="button" class="del" @click="removeCart(c.productId)">删除</button>
            </div>
          </li>
        </ul>
        <div v-else class="empty-box">
          <p>购物车是空的</p>
          <button type="button" class="link" @click="tab = 'browse'">去逛逛</button>
        </div>
        <div v-if="cart.length" class="cart-bar">
          <div class="cart-sum">
            <span>合计</span>
            <strong>¥{{ yuan(cartTotalCents) }}</strong>
          </div>
          <button type="button" class="primary bar-btn" :disabled="busy" @click="checkoutCart">
            去结算
          </button>
        </div>
      </template>
    </section>

    <!-- 订单 -->
    <section v-else-if="tab === 'orders'" class="pane pad">
      <p v-if="!loggedIn" class="empty-box">登录后查看订单</p>
      <template v-else>
        <div class="order-toolbar">
          <div class="seg">
            <button
              type="button"
              :class="{ on: orderRole === 'buyer' }"
              @click="switchOrderRole('buyer')"
            >
              我买的
            </button>
            <button
              type="button"
              :class="{ on: orderRole === 'seller' }"
              @click="switchOrderRole('seller')"
            >
              我卖的
            </button>
          </div>
          <button type="button" class="link addr-btn" @click="openAddressManage">地址管理</button>
        </div>

        <p v-if="loading" class="tip">加载中…</p>
        <ul v-else-if="currentOrders.length" class="olist">
          <li v-for="o in currentOrders" :key="o.id" class="ocard">
            <div class="oh">
              <div class="oh-left">
                <span class="oid">订单 #{{ o.id }}</span>
                <span v-if="o.createdAt" class="otime">{{ formatTime(o.createdAt) }}</span>
              </div>
              <em class="status-pill" :class="statusClass(o.status)">
                {{ STATUS_LABEL[o.status] || o.status }}
              </em>
            </div>

            <div v-for="it in o.items || []" :key="it.id" class="oline">
              <SignedImg v-if="it.coverUrl" class="thumb" :src="it.coverUrl" alt="" />
              <div v-else class="thumb ph" />
              <div class="oline-meta">
                <strong class="clamp2">{{ it.title }}</strong>
                <div class="oline-sub">
                  <span>¥{{ yuan(it.priceCents) }}</span>
                  <span class="muted">× {{ it.quantity }}</span>
                </div>
              </div>
            </div>

            <div class="ofoot">
              <div class="ototal">
                合计 <strong>¥{{ yuan(o.amountCents) }}</strong>
              </div>
              <div v-if="addressContact(o.addressSnapshot)" class="oaddr">
                <span>{{ addressContact(o.addressSnapshot) }}</span>
                <small v-if="addressLine(o.addressSnapshot)">{{ addressLine(o.addressSnapshot) }}</small>
              </div>
            </div>

            <div class="actions">
              <template v-if="orderRole === 'buyer'">
                <button
                  v-if="o.status === 'CREATED'"
                  type="button"
                  class="primary sm"
                  :disabled="busy"
                  @click="actOrder(() => marketApi().pay(o.id), '支付成功')"
                >
                  模拟支付
                </button>
                <button
                  v-if="o.status === 'CREATED'"
                  type="button"
                  class="ghost sm"
                  :disabled="busy"
                  @click="actOrder(() => marketApi().cancelOrder(o.id), '已取消')"
                >
                  取消
                </button>
                <button
                  v-if="o.status === 'SHIPPED'"
                  type="button"
                  class="primary sm"
                  :disabled="busy"
                  @click="actOrder(() => marketApi().confirmOrder(o.id), '已确认收货')"
                >
                  确认收货
                </button>
                <button
                  v-if="o.status === 'PAID' || o.status === 'SHIPPED' || o.status === 'COMPLETED'"
                  type="button"
                  class="ghost sm"
                  :disabled="busy"
                  @click="actOrder(() => marketApi().afterSale(o.id, '申请售后'), '已提交售后')"
                >
                  申请售后
                </button>
              </template>
              <template v-else>
                <button
                  v-if="o.status === 'PAID'"
                  type="button"
                  class="primary sm"
                  :disabled="busy"
                  @click="actOrder(() => marketApi().ship(o.id), '已发货')"
                >
                  发货
                </button>
                <button
                  v-if="o.afterSale && o.afterSale.status === 'PENDING'"
                  type="button"
                  class="primary sm"
                  :disabled="busy"
                  @click="actOrder(() => marketApi().agreeAfterSale(o.afterSale.id), '已同意退款')"
                >
                  同意售后
                </button>
                <button
                  v-if="o.afterSale && o.afterSale.status === 'PENDING'"
                  type="button"
                  class="ghost sm"
                  :disabled="busy"
                  @click="actOrder(() => marketApi().rejectAfterSale(o.afterSale.id), '已拒绝')"
                >
                  拒绝售后
                </button>
              </template>
            </div>
          </li>
        </ul>
        <div v-else class="empty-box">
          <p>暂无订单</p>
          <button v-if="orderRole === 'buyer'" type="button" class="link" @click="tab = 'browse'">
            去逛逛
          </button>
        </div>
      </template>
    </section>

    <!-- 卖家 -->
    <section v-else class="pane pad">
      <p v-if="!loggedIn" class="empty-box">登录后上架商品</p>
      <template v-else>
        <div class="sell-form">
          <h3 class="block-title">上架商品</h3>
          <label class="cover">
            <input type="file" accept="image/*" hidden @change="onSellCover" />
            <SignedImg
              v-if="sellAccessUrl || sellForm.coverUrl"
              :src="sellAccessUrl || sellForm.coverUrl"
              alt=""
            />
            <div v-else class="cover-ph">
              <span>点击上传封面</span>
              <small>建议竖图，清晰展示商品</small>
            </div>
          </label>
          <label class="field">
            商品标题
            <input v-model="sellForm.title" placeholder="简洁描述你的商品" maxlength="128" />
          </label>
          <label class="field">
            描述（可选）
            <textarea v-model="sellForm.description" rows="3" placeholder="成色、规格、寄出说明…" />
          </label>
          <div class="two">
            <label class="field">
              价格（元）
              <input
                v-model="sellForm.priceYuan"
                type="number"
                min="0.01"
                step="0.01"
                placeholder="0.00"
              />
            </label>
            <label class="field">
              库存
              <input v-model="sellForm.stock" type="number" min="0" step="1" placeholder="10" />
            </label>
          </div>
          <button type="button" class="primary" :disabled="busy" @click="publishProduct">
            {{ busy ? '提交中…' : '上架' }}
          </button>
        </div>

        <h3 class="block-title mt">我的商品</h3>
        <p v-if="loading" class="tip">加载中…</p>
        <ul v-else-if="sellerProducts.length" class="sell-list">
          <li v-for="p in sellerProducts" :key="p.id" class="sell-row">
            <SignedImg v-if="p.coverUrl" class="thumb" :src="p.coverUrl" alt="" />
            <div v-else class="thumb ph" />
            <div class="sell-meta">
              <div class="sell-top">
                <strong class="clamp2">{{ p.title }}</strong>
                <em class="status-pill" :class="p.status === 'ON' ? 'st-done' : 'st-muted'">
                  {{ p.status === 'ON' ? '在售' : '已下架' }}
                </em>
              </div>
              <div class="sell-sub">
                <span class="price">¥{{ yuan(p.priceCents) }}</span>
                <span class="muted">库存 {{ p.stock }}</span>
              </div>
            </div>
            <button type="button" class="ghost sm" :disabled="busy" @click="toggleProduct(p)">
              {{ p.status === 'ON' ? '下架' : '上架' }}
            </button>
          </li>
        </ul>
        <div v-else class="empty-box">
          <p>还没有上架商品</p>
        </div>
      </template>
    </section>

    <!-- 商品详情 -->
    <div v-if="detail" class="mask" @click.self="detail = null">
      <div class="sheet">
        <div class="sheet-hd">
          <strong>商品详情</strong>
          <button type="button" class="x" aria-label="关闭" @click="detail = null">×</button>
        </div>
        <div class="sheet-bd">
          <SignedImg v-if="detail.coverUrl" class="dcover" :src="detail.coverUrl" alt="" />
          <div v-else class="ph tall" />
          <h2 class="dtitle">{{ detail.title }}</h2>
          <p class="price big">¥{{ yuan(detail.priceCents) }}</p>
          <p class="muted seller-line">
            库存 {{ detail.stock }} · 卖家
            <button
              v-if="detail.sellerId"
              type="button"
              class="link"
              @click="$emit('open-profile', detail.sellerId)"
            >
              {{ detail.seller?.nickname || detail.seller?.username || `#${detail.sellerId}` }}
            </button>
          </p>
          <p class="desc">{{ detail.description || '暂无描述' }}</p>
          <div class="qty detail-qty">
            <span>数量</span>
            <button type="button" @click="buyQty = Math.max(1, buyQty - 1)">−</button>
            <span>{{ buyQty }}</span>
            <button type="button" @click="buyQty = Math.min(99, buyQty + 1)">+</button>
          </div>
        </div>
        <div class="sheet-ft dual">
          <button type="button" class="ghost" :disabled="busy" @click="addToCart">加入购物车</button>
          <button type="button" class="primary" :disabled="busy" @click="buyNow">立即购买</button>
        </div>
      </div>
    </div>

    <!-- 结算 -->
    <div v-if="checkoutOpen" class="mask" @click.self="checkoutOpen = false">
      <div class="sheet">
        <div class="sheet-hd">
          <strong>确认订单</strong>
          <button type="button" class="x" aria-label="关闭" @click="checkoutOpen = false">×</button>
        </div>
        <div class="sheet-bd">
          <div v-for="i in checkoutItems" :key="i.productId" class="ck-line">
            <SignedImg v-if="i.coverUrl" class="thumb" :src="i.coverUrl" alt="" />
            <div v-else class="thumb ph" />
            <div class="ck-meta">
              <strong class="clamp2">{{ i.title }}</strong>
              <div class="ck-sub">
                <span class="muted">× {{ i.quantity }}</span>
                <span class="price">¥{{ yuan(Number(i.priceCents) * Number(i.quantity)) }}</span>
              </div>
            </div>
          </div>

          <p class="ck-total">
            合计 <strong>¥{{ yuan(checkoutTotalCents) }}</strong>
          </p>

          <h3 class="block-title sm">收货地址</h3>
          <p v-if="!addresses.length" class="muted pad-y">暂无地址，请先添加</p>
          <button
            v-for="a in addresses"
            :key="a.id"
            type="button"
            class="addr-card"
            :class="{ on: selectedAddressId === a.id }"
            @click="selectedAddressId = a.id"
          >
            <div class="addr-top">
              <strong>{{ a.receiverName }} {{ a.phone }}</strong>
              <em v-if="a.isDefault === 1" class="def">默认</em>
            </div>
            <small>{{ a.province }}{{ a.city }}{{ a.district }}{{ a.detail }}</small>
          </button>
          <button type="button" class="link add-addr" @click="addressFormOpen = true">
            + 新增地址
          </button>
        </div>
        <div class="sheet-ft">
          <button type="button" class="primary" :disabled="busy" @click="submitOrder">
            {{ busy ? '提交中…' : '提交订单' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 地址表单 -->
    <div v-if="addressFormOpen" class="mask" @click.self="addressFormOpen = false">
      <div class="sheet">
        <div class="sheet-hd">
          <strong>地址管理</strong>
          <button type="button" class="x" aria-label="关闭" @click="addressFormOpen = false">×</button>
        </div>
        <div class="sheet-bd">
          <h3 class="block-title sm">新增地址</h3>
          <label class="field">
            收件人
            <input v-model="addressForm.receiverName" placeholder="姓名" />
          </label>
          <label class="field">
            手机号
            <input v-model="addressForm.phone" placeholder="11 位手机号" />
          </label>
          <div class="two">
            <label class="field">
              省
              <input v-model="addressForm.province" placeholder="省" />
            </label>
            <label class="field">
              市
              <input v-model="addressForm.city" placeholder="市" />
            </label>
          </div>
          <label class="field">
            区（可选）
            <input v-model="addressForm.district" placeholder="区" />
          </label>
          <label class="field">
            详细地址
            <input v-model="addressForm.detail" placeholder="街道门牌号" />
          </label>
          <label class="chk">
            <input v-model="addressForm.isDefault" type="checkbox" />
            设为默认
          </label>

          <ul v-if="addresses.length" class="alist">
            <li v-for="a in addresses" :key="a.id">
              <div>
                <strong>{{ a.receiverName }} {{ a.phone }}</strong>
                <em v-if="a.isDefault === 1">默认</em>
              </div>
              <small>{{ a.province }}{{ a.city }}{{ a.district }}{{ a.detail }}</small>
            </li>
          </ul>
        </div>
        <div class="sheet-ft">
          <button type="button" class="primary" :disabled="busy" @click="saveAddress">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.market {
  min-height: 100%;
  background: var(--bg);
  padding-bottom: calc(72px + var(--safe-bottom));
}

@media (min-width: 769px) {
  .market {
    padding-top: 8px;
    padding-bottom: 24px;
  }
  .m-head,
  .pane {
    max-width: 1100px;
    margin-left: auto;
    margin-right: auto;
  }
  .m-head {
    border-radius: 12px;
    border: 1px solid var(--line);
  }
  .grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

.m-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
  padding: 0.7rem 1rem;
  background: #fff;
}

.tabs {
  display: flex;
  gap: 0.35rem;
  margin-left: auto;
  flex-wrap: wrap;
}

.tabs button {
  border: 0;
  background: #f5f5f5;
  color: var(--ink-2);
  border-radius: 999px;
  padding: 0.3rem 0.75rem;
  font-size: 0.82rem;
}

.tabs button.on {
  background: var(--brand);
  color: #fff;
}

.badge {
  margin-left: 0.25rem;
  background: #fff;
  color: var(--brand);
  border-radius: 999px;
  padding: 0 0.35rem;
  font-size: 0.72rem;
}

.tabs button.on .badge {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 10px 10px 16px;
}

.sku {
  background: var(--card);
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  padding-bottom: 0.6rem;
  animation: ff-card-in var(--dur) var(--ease-out) both;
}

.sku-img,
.dcover {
  width: 100%;
  display: block;
  object-fit: cover;
}

.sku-img,
.ph {
  height: 140px;
}

.ph {
  background: repeating-linear-gradient(
    -45deg,
    #f3f3f3,
    #f3f3f3 8px,
    #ececec 8px,
    #ececec 16px
  );
}

.ph.tall {
  height: 180px;
  border-radius: 10px;
}

.sku h3 {
  margin: 0.5rem 0.6rem 0.2rem;
  font-size: 0.86rem;
  font-weight: 500;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.price {
  margin: 0 0.6rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}

.price.big {
  margin: 0.35rem 0;
  font-size: 1.35rem;
}

.pad {
  padding: 0 12px 16px;
}

.pad.has-bar {
  padding-bottom: calc(72px + var(--safe-bottom));
}

.tip {
  color: var(--ink-2);
  font-size: 0.86rem;
  padding: 1rem 0;
  text-align: center;
}

.muted {
  color: var(--ink-2);
  font-size: 0.82rem;
}

.empty-box {
  text-align: center;
  padding: 2.5rem 1rem;
  color: var(--ink-3);
}

.empty-box p {
  margin: 0 0 0.5rem;
}

.clamp2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.thumb {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
  display: block;
}

.thumb.lg {
  width: 88px;
  height: 88px;
  border-radius: 10px;
}

/* —— 购物车 —— */
.cart-list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
}

.cart-row {
  display: flex;
  gap: 12px;
  background: var(--card);
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 8px;
  position: relative;
  animation: ff-card-in var(--dur) var(--ease-out) both;
}

.cart-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.cart-meta strong {
  font-size: 0.9rem;
  font-weight: 500;
  line-height: 1.35;
}

.cart-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.cart-foot .price {
  margin: 0;
}

.del {
  align-self: flex-start;
  margin-top: -0.1rem;
  font-size: 0.78rem;
  color: var(--ink-3);
  padding: 0;
}

.del:hover {
  color: var(--brand);
}

.qty {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.qty button {
  width: 28px;
  height: 28px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 8px;
  font-size: 0.95rem;
  line-height: 1;
}

.qty span {
  min-width: 1.2rem;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.cart-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(56px + var(--safe-bottom));
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid var(--line);
  box-shadow: 0 -4px 16px rgba(15, 23, 42, 0.04);
}

@media (min-width: 769px) {
  .cart-bar {
    position: sticky;
    bottom: 0;
    left: auto;
    right: auto;
    margin: 12px auto 0;
    max-width: 1100px;
    border-radius: 12px;
    border: 1px solid var(--line);
    box-shadow: none;
  }
  .pad.has-bar {
    padding-bottom: 16px;
  }
}

.cart-sum {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: var(--ink-2);
}

.cart-sum strong {
  font-size: 1.15rem;
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}

.bar-btn {
  width: auto !important;
  margin: 0 !important;
  min-width: 110px;
  padding: 0.55rem 1.2rem;
}

/* —— 订单 —— */
.order-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 12px 0 10px;
}

.seg {
  display: inline-flex;
  padding: 3px;
  background: #f0f1f3;
  border-radius: 10px;
}

.seg button {
  border: 0;
  background: transparent;
  color: var(--ink-2);
  border-radius: 8px;
  padding: 0.35rem 0.9rem;
  font-size: 0.82rem;
  font-weight: 500;
}

.seg button.on {
  background: #fff;
  color: var(--ink);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.addr-btn {
  flex-shrink: 0;
}

.olist {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ocard {
  background: var(--card);
  border-radius: 12px;
  padding: 0;
  margin-bottom: 10px;
  overflow: hidden;
  animation: ff-card-in var(--dur) var(--ease-out) both;
}

.oh {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 12px 14px 8px;
}

.oh-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.oid {
  font-size: 0.84rem;
  font-weight: 500;
}

.otime {
  font-size: 0.72rem;
  color: var(--ink-3);
}

.status-pill {
  flex-shrink: 0;
  font-style: normal;
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  line-height: 1.4;
}

.st-created {
  color: #c2410c;
  background: #fff7ed;
}

.st-active {
  color: var(--brand);
  background: var(--brand-soft);
}

.st-done {
  color: #047857;
  background: #ecfdf5;
}

.st-muted {
  color: var(--ink-2);
  background: #f1f5f9;
}

.st-warn {
  color: #b45309;
  background: #fffbeb;
}

.oline {
  display: flex;
  gap: 10px;
  padding: 6px 14px;
}

.oline-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.25rem;
}

.oline-meta strong {
  font-size: 0.86rem;
  font-weight: 500;
}

.oline-sub {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.82rem;
}

.oline-sub .price,
.oline-sub span:first-child {
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.ofoot {
  padding: 8px 14px 4px;
  border-top: 1px solid #f3f4f6;
  margin-top: 4px;
}

.ototal {
  text-align: right;
  font-size: 0.82rem;
  color: var(--ink-2);
}

.ototal strong {
  font-size: 1rem;
  color: var(--ink);
  margin-left: 0.25rem;
  font-variant-numeric: tabular-nums;
}

.oaddr {
  margin-top: 6px;
  font-size: 0.78rem;
  color: var(--ink-2);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.oaddr small {
  color: var(--ink-3);
  font-size: 0.74rem;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 14px 12px;
}

/* —— 卖家 —— */
.block-title {
  margin: 0 0 0.65rem;
  font-size: 0.95rem;
  font-weight: 600;
}

.block-title.sm {
  margin-top: 0.75rem;
  font-size: 0.86rem;
  color: var(--ink-2);
  font-weight: 500;
}

.block-title.mt {
  margin-top: 1.25rem;
}

.sell-form {
  background: var(--card);
  border-radius: 12px;
  padding: 14px;
  margin-top: 10px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin-bottom: 0.65rem;
  font-size: 0.78rem;
  color: var(--ink-2);
}

.field input,
.field textarea,
.sell-form input,
.sell-form textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 0.55rem 0.7rem;
  background: #fafafa;
  color: var(--ink);
  font: inherit;
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  outline: none;
  border-color: var(--brand);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(255, 36, 66, 0.12);
}

.cover {
  display: block;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  margin-bottom: 0.75rem;
  border: 1.5px dashed var(--line);
  background: #fafafa;
}

.cover img {
  width: 100%;
  height: 160px;
  object-fit: cover;
  display: block;
}

.cover-ph {
  height: 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
  color: var(--ink-2);
  font-size: 0.86rem;
}

.cover-ph small {
  font-size: 0.72rem;
  color: var(--ink-3);
}

.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.sell-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.sell-row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--card);
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 8px;
}

.sell-meta {
  flex: 1;
  min-width: 0;
}

.sell-top {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
}

.sell-top strong {
  flex: 1;
  font-size: 0.88rem;
  font-weight: 500;
}

.sell-sub {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.3rem;
}

.sell-sub .price {
  margin: 0;
  font-size: 0.86rem;
}

/* —— 按钮 —— */
.link {
  border: 0;
  background: none;
  color: var(--brand);
  cursor: pointer;
  font-size: 0.82rem;
  padding: 0;
}

.primary,
.ghost {
  border: 0;
  border-radius: 999px;
  padding: 0.65rem 1.2rem;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 600;
  width: 100%;
  margin-top: 4px;
}

.primary {
  background: var(--brand);
  color: #fff;
}

.ghost {
  background: #f5f5f5;
  color: var(--ink);
}

.primary:disabled,
.ghost:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.sm {
  width: auto;
  margin: 0;
  padding: 0.38rem 0.85rem;
  font-size: 0.8rem;
  border-radius: 999px;
}

.ghost.sm {
  background: #fff;
  border: 1px solid var(--line);
  color: var(--ink-2);
}

/* —— Sheet —— */
.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  animation: ff-fade-in var(--dur-fast) ease both;
}

.sheet {
  width: min(520px, 100%);
  max-height: min(88vh, 720px);
  background: #fff;
  border-radius: 16px 16px 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: ff-rise var(--dur) var(--ease-out) both;
}

@media (min-width: 769px) {
  .mask {
    align-items: center;
  }
  .sheet {
    border-radius: 16px;
    max-height: 80vh;
  }
}

.sheet-hd,
.sheet-ft {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--line);
  flex-shrink: 0;
}

.sheet-ft {
  border-bottom: none;
  border-top: 1px solid var(--line);
}

.sheet-ft.dual .primary,
.sheet-ft.dual .ghost {
  flex: 1;
  margin: 0;
}

.sheet-ft .primary {
  margin: 0;
}

.sheet-hd .x {
  border: none;
  background: transparent;
  font-size: 1.4rem;
  line-height: 1;
  cursor: pointer;
  color: var(--ink-3);
  padding: 0 0.2rem;
}

.sheet-bd {
  padding: 0.85rem 1rem 1rem;
  overflow: auto;
  flex: 1;
}

.dcover {
  height: 220px;
  border-radius: 12px;
}

.dtitle {
  margin: 0.75rem 0 0;
  font-size: 1.1rem;
  font-weight: 600;
  line-height: 1.35;
}

.seller-line {
  margin: 0.35rem 0 0;
}

.desc {
  font-size: 0.9rem;
  color: var(--ink);
  white-space: pre-wrap;
  margin: 0.75rem 0;
  line-height: 1.55;
}

.detail-qty {
  margin: 0.5rem 0 0.25rem;
  gap: 0.6rem;
}

.detail-qty > span:first-child {
  font-size: 0.86rem;
  color: var(--ink-2);
}

.ck-line {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}

.ck-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.3rem;
}

.ck-meta strong {
  font-size: 0.88rem;
  font-weight: 500;
}

.ck-sub {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ck-sub .price {
  margin: 0;
  font-size: 0.88rem;
}

.ck-total {
  text-align: right;
  margin: 12px 0 4px;
  font-size: 0.86rem;
  color: var(--ink-2);
}

.ck-total strong {
  font-size: 1.2rem;
  color: var(--brand);
  margin-left: 0.3rem;
  font-variant-numeric: tabular-nums;
}

.pad-y {
  padding: 0.5rem 0;
}

.addr-card {
  display: block;
  width: 100%;
  text-align: left;
  border: 1.5px solid var(--line);
  background: #fff;
  border-radius: 12px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
}

.addr-card.on {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.addr-top {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 2px;
}

.addr-top strong {
  font-size: 0.88rem;
  font-weight: 500;
}

.addr-card small {
  display: block;
  color: var(--ink-2);
  font-size: 0.78rem;
  line-height: 1.4;
}

.def {
  font-style: normal;
  font-size: 0.68rem;
  color: var(--brand);
  background: #fff;
  border: 1px solid var(--brand-soft);
  border-radius: 4px;
  padding: 0 0.3rem;
}

.add-addr {
  margin: 4px 0 8px;
}

.chk {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.86rem;
  margin: 6px 0 12px;
  color: var(--ink-2);
}

.alist {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
  border-top: 1px solid var(--line);
}

.alist li {
  padding: 10px 0;
  border-bottom: 1px solid #f3f4f6;
}

.alist strong {
  font-size: 0.84rem;
  font-weight: 500;
}

.alist em {
  font-style: normal;
  font-size: 0.7rem;
  color: var(--brand);
  margin-left: 6px;
}

.alist small {
  display: block;
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 0.76rem;
}
</style>
