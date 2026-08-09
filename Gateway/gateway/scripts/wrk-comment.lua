-- wrk 发评论：POST /api/social/comment
-- 环境变量 NOTE_ID 默认 1（wrk 不直接读 env，由脚本内写死或改此文件）
local noteId = os.getenv("NOTE_ID") or "1"

request = function()
  local body = string.format(
    '{"noteId":%s,"content":"bench-%d","parentId":null}',
    noteId,
    math.random(1, 1000000000)
  )
  return wrk.format("POST", nil, {
    ["Content-Type"] = "application/json",
  }, body)
end
