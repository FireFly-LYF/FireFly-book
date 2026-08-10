<script setup>
import TodoBadge from '../components/TodoBadge.vue'
</script>

<template>
  <div class="market">
    <header>
      <strong>市集</strong>
      <TodoBadge text="电商待实现" />
    </header>
    <div class="hero">
      <h2>FireFly 市集</h2>
      <p>仿小红书购物频道：商品卡、直播入口、订单中心</p>
    </div>
    <div class="grid">
      <article v-for="i in 4" :key="i" class="sku">
        <div class="ph" />
        <h3>示例商品 {{ i }}</h3>
        <p>¥ — <TodoBadge /></p>
      </article>
    </div>
  </div>
</template>

<style scoped>
.market {
  min-height: 100%;
  background: var(--bg);
}

@media (min-width: 769px) {
  .market { padding-top: 8px; }
  header {
    max-width: 1100px;
    margin: 0 auto;
    border-radius: 12px;
    border: 1px solid var(--line);
  }
  .hero, .grid {
    max-width: 1100px;
    margin-left: auto;
    margin-right: auto;
  }
  .grid { grid-template-columns: repeat(4, 1fr); }
}

header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.8rem 1rem;
  background: #fff;
}

.hero {
  margin: 8px;
  padding: 1.2rem;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffecef, #fff);
  color: var(--ink);
}

.hero h2 {
  margin: 0 0 0.35rem;
  font-family: 'ZCOOL XiaoWei', serif;
  color: var(--xhs-red);
}

.hero p { margin: 0; color: var(--ink-2); font-size: 0.86rem; }

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 0 8px;
}

.sku {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  padding-bottom: 0.6rem;
}

.ph {
  height: 120px;
  background: repeating-linear-gradient(
    -45deg,
    #f3f3f3,
    #f3f3f3 8px,
    #ececec 8px,
    #ececec 16px
  );
}

h3 {
  margin: 0.5rem 0.55rem 0.2rem;
  font-size: 0.86rem;
  font-weight: 500;
}

p {
  margin: 0 0.55rem;
  font-size: 0.8rem;
  color: var(--xhs-red);
}
</style>
