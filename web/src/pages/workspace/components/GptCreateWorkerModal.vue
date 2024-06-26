<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules } from 'naive-ui';

import { Locator } from '@/data';

const workspace = Locator.gptWorkspaceRepository();
const workspaceRef = workspace.ref;

const formRef = ref<FormInst>();
const formValue = ref<{
  id: string;
  type: 'web' | 'api';
  modelWeb: string;
  modelApi: string;
  endpointWeb: string;
  endpointApi: string;
  key: string;
}>({
  id: '',
  type: 'web',
  modelWeb: 'text-davinci-002-render-sha',
  modelApi: 'gpt-3.5-turbo',
  endpointWeb: 'https://chat.openai.com/backend-api',
  endpointApi: 'https://api.openai.com',
  key: '',
});

const emptyCheck = (name: string) => ({
  validator: (rule: FormItemRule, value: string) => value.trim().length > 0,
  message: name + '不能为空',
  trigger: 'input',
});

const formRules: FormRules = {
  id: [
    emptyCheck('名字'),
    {
      validator: (rule: FormItemRule, value: string) =>
        workspaceRef.value.workers.find(({ id }) => id === value) === undefined,
      message: '名字不能重复',
      trigger: 'input',
    },
  ],
  modelWeb: [emptyCheck('模型')],
  modelApi: [emptyCheck('模型')],
  endpointWeb: [emptyCheck('链接')],
  endpointApi: [emptyCheck('链接')],
  key: [
    emptyCheck('Key'),
    {
      validator: (rule: FormItemRule, value: string) =>
        workspaceRef.value.workers.find(({ key }) => key === value) ===
        undefined,
      message: 'Key不能重复',
      trigger: 'input',
    },
  ],
};

const createGptWorker = async () => {
  const validated = await new Promise<boolean>(function (resolve, _reject) {
    formRef.value?.validate((errors) => {
      if (errors) resolve(false);
      else resolve(true);
    });
  });
  if (!validated) return;

  const { id, type, modelWeb, modelApi, endpointWeb, endpointApi, key } =
    formValue.value;
  const worker = {
    id: id.trim(),
    type,
    model: type === 'web' ? modelWeb.trim() : modelApi.trim(),
    endpoint: type === 'web' ? endpointWeb.trim() : endpointApi.trim(),
    key: key.trim(),
  };
  try {
    const obj = JSON.parse(worker.key);
    if (typeof obj.accessToken === 'string') {
      worker.key = obj.accessToken;
    }
  } catch {}
  workspace.addWorker(worker);
};
</script>

<template>
  <c-modal title="添加GPT翻译器">
    <n-form
      ref="formRef"
      :model="formValue"
      :rules="formRules"
      label-placement="left"
      label-width="auto"
    >
      <n-form-item-row path="id" label="名字">
        <n-input
          v-model:value="formValue.id"
          placeholder="给你的翻译器起个名字"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
      <n-form-item-row path="type" label="类型">
        <n-radio-group v-model:value="formValue.type" name="type">
          <n-flex>
            <n-radio value="web">Web</n-radio>
            <n-radio value="api">Api</n-radio>
          </n-flex>
        </n-radio-group>
      </n-form-item-row>

      <template v-if="formValue.type === 'web'">
        <n-form-item-row path="modelWeb" label="模型">
          <n-input
            v-model:value="formValue.modelWeb"
            disabled
            placeholder="模型名称"
            :input-props="{ spellcheck: false }"
          />
        </n-form-item-row>
        <n-form-item-row path="endpointWeb" label="链接">
          <n-input
            v-model:value="formValue.endpointWeb"
            placeholder="OpenAI链接，可以使用第三方中转"
            :input-props="{ spellcheck: false }"
          />
        </n-form-item-row>
      </template>

      <template v-else>
        <n-form-item-row path="modelApi" label="模型">
          <n-input
            v-model:value="formValue.modelApi"
            placeholder="模型名称"
            :input-props="{ spellcheck: false }"
          />
        </n-form-item-row>
        <n-form-item-row path="endpointApi" label="链接">
          <n-input
            v-model:value="formValue.endpointApi"
            placeholder="OpenAI链接，可以使用第三方中转"
            :input-props="{ spellcheck: false }"
          />
        </n-form-item-row>
      </template>

      <n-form-item-row path="key" label="Key">
        <n-input
          v-model:value="formValue.key"
          :placeholder="
            formValue.type === 'web' ? '请输入Access token' : '请输入Api key'
          "
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-text depth="3" style="font-size: 12px">
        {{
          formValue.type === 'web'
            ? '# 链接例子：https://chatgpt-proxy.lss233.com/api'
            : '# 链接例子：https://gpt.mnxcc.com，后面不要加‘/v1’'
        }}
      </n-text>
    </n-form>

    <template #action>
      <c-button label="添加" type="primary" @action="createGptWorker" />
    </template>
  </c-modal>
</template>
