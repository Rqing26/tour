-- 线路图片上传（T4）：新图落到磁盘，表里只记相对文件名，图片数量因此不再受 6 个枚举键限制。
-- 只新增可空列（C2、C3），已有线路无需回填：image_path 为空时前端回退到 image_key（C7）。
-- image_key 保持 NOT NULL 不动，它仍是旧线路与演示数据的图片来源。
ALTER TABLE travel_routes ADD COLUMN image_path VARCHAR(200);
