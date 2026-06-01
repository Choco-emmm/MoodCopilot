ALTER TABLE diaries
  ADD COLUMN image_meta JSON NULL COMMENT '图片上传与压缩元数据（通道、尺寸、体积、质量）' AFTER images;
