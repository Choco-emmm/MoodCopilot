import re

with open("frontend/src/components/memory/MemoryProfileView.vue", "r", encoding="utf-8") as f:
    code = f.read()

if "const showApproveModal" not in code:
    code = code.replace("const editingMemoryIsCore = ref(false)", "const editingMemoryIsCore = ref(false)\nconst showApproveModal = ref(false)\nconst showRejectModal = ref(false)\nconst activeCandidateId = ref<number | null>(null)\nconst rejectReason = ref('')\nconst approveEditedValue = ref('')")

modal_html = """
    <!-- Approve Candidate Modal -->
    <n-modal v-model:show="showApproveModal" preset="dialog" title="确认候选记忆" :show-icon="false"
      positive-text="确认并保存" negative-text="取消" @positive-click="doApproveCandidate" @negative-click="showApproveModal = false">
      <div style="margin-top: 16px;">
        <p style="margin-bottom: 8px; color: var(--color-text-secondary);">你可以直接确认，也可以在这里修改后再确认：</p>
        <n-input v-model:value="approveEditedValue" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="请输入最终的记忆内容" />
      </div>
    </n-modal>

    <!-- Reject Candidate Modal -->
    <n-modal v-model:show="showRejectModal" preset="dialog" title="拒绝候选记忆" :show-icon="false"
      positive-text="确认拒绝" negative-text="取消" :positive-button-props="{ type: 'warning' }" @positive-click="doRejectCandidate" @negative-click="showRejectModal = false">
      <div style="margin-top: 16px;">
        <p style="margin-bottom: 8px; color: var(--color-text-secondary);">拒绝后，这条内容不会进入正式画像。你可以写下拒绝原因，AI 会根据你的原因重新思考并提取正确的记忆：</p>
        <n-input v-model:value="rejectReason" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="（可选）例如：这只是一时冲动，并不是我的习惯..." />
      </div>
    </n-modal>
"""
if "showApproveModal" not in code.split("</template>")[0]:
    code = code.replace("</template>", modal_html + "\n</template>", 1)

methods_update = """
async function approveCandidate(id: number) {
  const candidate = candidates.value.find(c => c.id === id)
  if (candidate) {
    approveEditedValue.value = candidate.attributeValue
    activeCandidateId.value = id
    showApproveModal.value = true
  }
}

async function doApproveCandidate() {
  if (activeCandidateId.value === null) return
  const id = activeCandidateId.value
  await memoryApi.approveCandidate(id, { editedValue: approveEditedValue.value })
  await loadMemories()
  resetMemoryDetails()
  window.$message?.success('记忆已确认')
  showApproveModal.value = false
}

function confirmRejectCandidate(candidate: any) {
  activeCandidateId.value = candidate.id
  rejectReason.value = ''
  showRejectModal.value = true
}

async function doRejectCandidate() {
  if (activeCandidateId.value === null) return
  const id = activeCandidateId.value
  if (rejectingCandidateId.value !== null) return
  rejectingCandidateId.value = id
  try {
    await memoryApi.rejectCandidate(id, { reason: rejectReason.value })
    candidates.value = candidates.value.filter(candidate => candidate.id !== id)
    if (candidateDetailsId.value === id) {
      candidateDetailsId.value = null
      candidateEvidence.value = []
    }
    window.$message?.success('已拒绝，这条内容近期不会再被自动加入')
    showRejectModal.value = false
  } catch (e) {
    logWarn('memory', '拒绝候选记忆失败', id, e)
  } finally {
    rejectingCandidateId.value = null
  }
}
"""

code = re.sub(r"async function approveCandidate\(id: number\).*?finally \{\s*rejectingCandidateId\.value = null\s*\}\s*\}", methods_update, code, flags=re.DOTALL)

if "NModal" not in code:
    code = code.replace("NPopover, useDialog", "NPopover, useDialog, NModal")

with open("frontend/src/components/memory/MemoryProfileView.vue", "w", encoding="utf-8") as f:
    f.write(code)
