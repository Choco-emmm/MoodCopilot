import os

p = "frontend-uniapp/src/pages/chat/chat.vue"
with open(p, "r", encoding="utf-8") as f:
    content = f.read()

target = '''        <view class="session-context">
          <text class="session-label">本次对话</text>
          <text class="current-session-title">{{ currentConversationTitle }}</text>
        </view>'''

replacement = '''        <view class="session-context" @click="editConversationTitle" style="cursor: pointer;">
          <text class="session-label">本次对话</text>
          <text class="current-session-title">{{ currentConversationTitle }} <text style="font-size: 20rpx; opacity: 0.6; margin-left: 8rpx;">✎</text></text>
        </view>'''

target2 = "import { onShow } from '@dcloudio/uni-app';"

replacement2 = '''import { onShow } from '@dcloudio/uni-app';

async function editConversationTitle() {
  if (!conversationId.value) return;
  uni.showModal({
    title: '修改对话名称',
    editable: true,
    placeholderText: '请输入新名称',
    success: async (res) => {
      if (res.confirm && res.content) {
        try {
          await put(/api/chat/conversations/$`{conversationId.value}/title, { title: res.content });
          const idx = conversations.value.findIndex(c => c.id === conversationId.value);
          if (idx !== -1) {
            conversations.value[idx].title = res.content;
          }
        } catch (e: any) {
          uni.showToast({ title: '修改失败', icon: 'none' });
        }
      }
    }
  });
}'''

content = content.replace('\r\n', '\n')
target = target.replace('\r\n', '\n')
target2 = target2.replace('\r\n', '\n')

if target in content and target2 in content:
    content = content.replace(target, replacement).replace(target2, replacement2)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Patched uniapp chat.vue")
else:
    print("Target not found in uniapp chat.vue: target=" + str(target in content) + ", target2=" + str(target2 in content))

