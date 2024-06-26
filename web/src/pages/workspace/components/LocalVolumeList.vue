<script lang="ts" setup>
import { MoreVertOutlined } from '@vicons/material';
import { UploadFileInfo } from 'naive-ui';

import { Locator } from '@/data';
import { LocalVolumeMetadata } from '@/model/LocalVolume';

const props = defineProps<{
  hideTitle?: boolean;
  options?: { [key: string]: (volumes: LocalVolumeMetadata[]) => void };
  filter?: (volume: LocalVolumeMetadata) => boolean;
  beforeVolumeAdd?: (file: File) => void;
}>();

const message = useMessage();

const volumes = ref<LocalVolumeMetadata[]>();

const loadVolumes = async () => {
  const repo = await Locator.localVolumeRepository();
  volumes.value = await repo.listVolume();
};
loadVolumes();

const onFinish = ({ file }: { file: UploadFileInfo }) => {
  if (props.beforeVolumeAdd) {
    props.beforeVolumeAdd(file.file!!);
  }
  loadVolumes();
};

const options = computed(() => {
  const options =
    props.options === undefined
      ? []
      : Object.keys(props.options).map((it) => ({
          label: it,
          key: it,
        }));
  options.push({ label: '清空文件', key: '清空文件' });
  return options;
});
const handleSelect = (key: string) => {
  if (key === '清空文件') {
    showClearModal.value = true;
  } else {
    props.options?.[key]?.(volumes.value ?? []);
  }
};

const showClearModal = ref(false);
const deleteAllVolumes = () =>
  Locator.localVolumeRepository()
    .then((repo) => repo.deleteVolumesDb())
    .then(loadVolumes)
    .then(() => (showClearModal.value = false))
    .catch((error) => {
      message.error(`清空失败:${error}`);
    });

const order = ref<'byCreateAt' | 'byId'>('byCreateAt');
const orderOptions = [
  { value: 'byCreateAt', label: '按添加时间' },
  { value: 'byId', label: '按文件名' },
];
const sortedVolumes = computed(() => {
  const filteredVolumes =
    props.filter === undefined
      ? volumes.value
      : volumes.value?.filter(props.filter);
  if (order.value === 'byId') {
    return filteredVolumes?.sort((a, b) => a.id.localeCompare(b.id));
  } else {
    return filteredVolumes?.sort((a, b) => b.createAt - a.createAt);
  }
});

const deleteVolume = (volumeId: string) =>
  Locator.localVolumeRepository()
    .then((repo) => repo.deleteVolume(volumeId))
    .then(() => message.info('删除成功'))
    .then(() => loadVolumes())
    .catch((error) => message.error(`删除失败：${error}`));

defineExpose({ deleteVolume });
</script>

<template>
  <section-header title="本地小说" v-if="!hideTitle">
    <n-flex :wrap="false">
      <add-button :show-file-list="false" @finish="onFinish" />
      <n-dropdown
        trigger="click"
        :options="options"
        :keyboard="false"
        @select="handleSelect"
      >
        <n-button circle>
          <n-icon :component="MoreVertOutlined" />
        </n-button>
      </n-dropdown>
    </n-flex>
  </section-header>

  <n-flex vertical>
    <c-action-wrapper title="排序">
      <c-radio-group
        v-model:value="order"
        :options="orderOptions"
        size="small"
      />
    </c-action-wrapper>

    <slot name="extra" />
  </n-flex>

  <n-divider style="margin: 16px 0 8px" />

  <n-spin v-if="sortedVolumes === undefined" style="margin-top: 20px" />

  <n-empty
    v-else-if="sortedVolumes.length === 0"
    description="没有文件"
    style="margin-top: 20px"
  />

  <n-scrollbar v-else trigger="none" :size="24" style="flex: auto">
    <n-list style="padding-bottom: 48px">
      <n-list-item v-for="volume of sortedVolumes ?? []" :key="volume.id">
        <slot name="volume" v-bind="volume" />
      </n-list-item>
    </n-list>
  </n-scrollbar>

  <c-modal title="清空所有文件" v-model:show="showClearModal">
    <n-p>
      这将清空你的浏览器里面保存的所有EPUB/TXT文件，包括已经翻译的章节和术语表，无法恢复。
      你确定吗？
    </n-p>

    <template #action>
      <c-button label="确定" type="primary" @action="deleteAllVolumes" />
    </template>
  </c-modal>
</template>
