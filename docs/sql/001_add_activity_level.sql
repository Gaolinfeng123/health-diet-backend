ALTER TABLE users
ADD COLUMN activity_level TINYINT NOT NULL DEFAULT 1 COMMENT '1=久坐 2=轻活动 3=中活动 4=高活动';
