import re

with open("frontend-uniapp/src/pages/analysis/analysis.vue", "r", encoding="utf-8") as f:
    code = f.read()

# Update approveCandidate
approve_replacement = """
const approveCandidate = async (id: number) => {
  if (candidateActionId.value !== null) return;
  const candidate = candidates.value.find(c => c.id === id);
  if (!candidate) return;

  uni.showModal({
    title: '确认候选记忆',
    content: candidate.attributeValue,
    editable: true,
    placeholderText: '你可以修改这段记忆...',
    confirmText: '确认并保存',
    cancelText: '取消',
    success: async (res) => {
      if (res.confirm) {
        candidateActionId.value = id;
        try {
          const editedValue = res.content || candidate.attributeValue;
          const apiRes = await post(`/api/memory/candidates/${id}/approve`, { editedValue });
          if (apiRes.code === 200) {
            candidates.value = candidates.value.filter(c => c.id !== id);
            await Promise.all([fetchMemory(), fetchCandidates()]);
            showMemoryDetailsModal.value = false;
            activeMemoryDetailsId.value = null;
            memoryEvidence.value = [];
            uni.showToast({ title: '已确认', icon: 'success' });
          }
        } catch (e) {
          uni.showToast({ title: '确认失败，请稍后再试', icon: 'none' });
        } finally {
          candidateActionId.value = null;
        }
      }
    }
  });
};
"""
code = re.sub(r"const approveCandidate = async \(id: number\) => \{.*?\n\};\n", approve_replacement + "\n", code, flags=re.DOTALL)

# Update rejectCandidate
reject_replacement = """
const rejectCandidate = async (id: number) => {
  if (candidateActionId.value !== null) return;
  
  uni.showModal({
    title: '拒绝候选记忆',
    content: '',
    editable: true,
    placeholderText: '（可选）拒绝原因...',
    confirmText: '确认拒绝',
    cancelText: '取消',
    confirmColor: '#d32f2f',
    success: async (res) => {
      if (res.confirm) {
        candidateActionId.value = id;
        try {
          const reason = res.content || '';
          const apiRes = await post(`/api/memory/candidates/${id}/reject`, { reason });
          if (apiRes.code === 200) {
            candidates.value = candidates.value.filter(candidate => candidate.id !== id);
            uni.showToast({ title: '已拒绝', icon: 'none' });
          }
        } catch (e) {
          uni.showToast({ title: '拒绝失败，请稍后再试', icon: 'none' });
        } finally {
          candidateActionId.value = null;
        }
      }
    }
  });
};
"""
code = re.sub(r"const rejectCandidate = async \(id: number\) => \{.*?\n\};\n", reject_replacement + "\n", code, flags=re.DOTALL)

with open("frontend-uniapp/src/pages/analysis/analysis.vue", "w", encoding="utf-8") as f:
    f.write(code)

print("Updated analysis.vue")
