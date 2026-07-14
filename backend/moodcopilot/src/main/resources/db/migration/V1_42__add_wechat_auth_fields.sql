ALTER TABLE users
ADD COLUMN wx_open_id VARCHAR(100) DEFAULT NULL COMMENT '微信OpenID',
ADD COLUMN wx_union_id VARCHAR(100) DEFAULT NULL COMMENT '微信UnionID';

CREATE UNIQUE INDEX idx_wx_open_id ON users(wx_open_id);
CREATE UNIQUE INDEX idx_wx_union_id ON users(wx_union_id);
