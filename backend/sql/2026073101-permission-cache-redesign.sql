CREATE TABLE t_system_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统版本ID',
    version_code VARCHAR(64) NOT NULL COMMENT '版本编码',
    version_value BIGINT UNSIGNED NOT NULL COMMENT '版本值',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_system_version_code (version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统版本';

INSERT INTO t_system_version (
    version_code, version_value, created_at, updated_at
) VALUES ('permission_snapshot', 0, NOW(3), NOW(3));
