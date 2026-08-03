-- 仅用于空库完成建表后的完整基础数据初始化。
-- 执行顺序：2026072201-system-init.sql -> 2026073101-permission-cache-redesign.sql
--          -> 2026080301-system-data-reinit.sql。
-- 使用本文件时不要再执行 2026073001-system-data-init.sql 和
-- 2026073102-task-management.sql。

SET @init_now = NOW(3);
SET @permission_changed = 0;

START TRANSACTION;

INSERT INTO t_department (
    parent_id, name, sort_order, status, created_at, updated_at
)
SELECT 0, '总部', 0, 1, @init_now, @init_now
WHERE NOT EXISTS (
    SELECT 1 FROM t_department WHERE parent_id = 0 AND name = '总部'
);

-- 默认管理员账号为 admin，密码哈希由 BCrypt 生成，初次登录后应立即修改密码。
INSERT INTO t_employee (
    username, name, password_hash, phone, email, department_id,
    status, is_superuser, created_at, updated_at
)
SELECT
    'admin',
    '超级管理员',
    '$2a$10$8jnMcI8Np8unWAA34nqEdOvhik2LW1fzt/nM9.cyti06nJmqf2HQC',
    NULL,
    NULL,
    department.id,
    1,
    1,
    @init_now,
    @init_now
FROM t_department AS department
WHERE department.parent_id = 0
  AND department.name = '总部'
  AND NOT EXISTS (SELECT 1 FROM t_employee WHERE username = 'admin');

DROP TEMPORARY TABLE IF EXISTS tmp_system_api_definition;
CREATE TEMPORARY TABLE tmp_system_api_definition (
    name VARCHAR(100) NOT NULL,
    path VARCHAR(255) NOT NULL,
    description VARCHAR(500) NULL,
    PRIMARY KEY (path)
);

INSERT INTO tmp_system_api_definition (name, path, description) VALUES
    ('员工分页', '/api/system/employees/page', '分页查询员工'),
    ('员工详情', '/api/system/employees/detail', '查询员工详情'),
    ('员工角色ID', '/api/system/employees/role-ids', '查询员工角色ID'),
    ('新增员工', '/api/system/employees/create', '新增员工'),
    ('修改员工', '/api/system/employees/update', '修改员工'),
    ('修改员工状态', '/api/system/employees/status', '修改员工状态'),
    ('删除员工', '/api/system/employees/delete', '删除员工'),
    ('重置员工密码', '/api/system/employees/reset-password', '重置员工密码'),
    ('保存员工角色', '/api/system/employees/roles', '保存员工角色'),
    ('部门树', '/api/system/departments/tree', '查询部门树'),
    ('部门详情', '/api/system/departments/detail', '查询部门详情'),
    ('新增部门', '/api/system/departments/create', '新增部门'),
    ('修改部门', '/api/system/departments/update', '修改部门'),
    ('修改部门状态', '/api/system/departments/status', '修改部门状态'),
    ('删除部门', '/api/system/departments/delete', '删除部门'),
    ('角色分页', '/api/system/roles/page', '分页查询角色'),
    ('角色详情', '/api/system/roles/detail', '查询角色详情'),
    ('角色资源ID', '/api/system/roles/resource-ids', '查询角色资源ID'),
    ('新增角色', '/api/system/roles/create', '新增角色'),
    ('修改角色', '/api/system/roles/update', '修改角色'),
    ('修改角色状态', '/api/system/roles/status', '修改角色状态'),
    ('删除角色', '/api/system/roles/delete', '删除角色'),
    ('保存角色资源', '/api/system/roles/resources', '保存角色资源'),
    ('资源树', '/api/system/resources/tree', '查询资源树'),
    ('资源详情', '/api/system/resources/detail', '查询资源详情'),
    ('新增资源', '/api/system/resources/create', '新增资源'),
    ('修改资源', '/api/system/resources/update', '修改资源'),
    ('修改资源状态', '/api/system/resources/status', '修改资源状态'),
    ('删除资源', '/api/system/resources/delete', '删除资源'),
    ('资源接口ID', '/api/system/resources/api-ids', '查询资源接口ID'),
    ('保存资源接口', '/api/system/resources/apis', '保存资源接口'),
    ('接口分页', '/api/system/apis/page', '分页查询系统接口'),
    ('接口详情', '/api/system/apis/detail', '查询系统接口详情'),
    ('修改接口状态', '/api/system/apis/status', '修改系统接口状态'),
    ('任务列表', '/api/admin/task/list.htm', '查询后台任务列表'),
    ('执行任务', '/api/admin/task/run.htm', '手动执行后台任务');

INSERT INTO t_system_api (name, path, description, status, created_at, updated_at)
SELECT definition.name, definition.path, definition.description, 1, @init_now, @init_now
FROM tmp_system_api_definition AS definition
WHERE NOT EXISTS (
    SELECT 1 FROM t_system_api AS existing WHERE existing.path = definition.path
);
SET @permission_changed = @permission_changed + ROW_COUNT();

DROP TEMPORARY TABLE IF EXISTS tmp_system_resource_definition;
CREATE TEMPORARY TABLE tmp_system_resource_definition (
    parent_code VARCHAR(100) NULL,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL,
    path VARCHAR(255) NULL,
    sort_order INT NOT NULL,
    visible TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (code)
);

INSERT INTO tmp_system_resource_definition (
    parent_code, type, name, code, path, sort_order, visible
) VALUES
    (NULL, 'DIRECTORY', '系统管理', 'system', NULL, 0, 1),
    ('system', 'MENU', '员工管理', 'system:employee', '/system/employees', 1, 1),
    ('system:employee', 'ACTION', '新增员工', 'system:employee:create', NULL, 2, 0),
    ('system:employee', 'ACTION', '修改员工', 'system:employee:update', NULL, 3, 0),
    ('system:employee', 'ACTION', '修改员工状态', 'system:employee:status', NULL, 4, 0),
    ('system:employee', 'ACTION', '删除员工', 'system:employee:delete', NULL, 5, 0),
    ('system:employee', 'ACTION', '重置员工密码', 'system:employee:reset-password', NULL, 6, 0),
    ('system:employee', 'ACTION', '分配员工角色', 'system:employee:roles', NULL, 7, 0),
    ('system', 'MENU', '部门管理', 'system:department', '/system/departments', 8, 1),
    ('system:department', 'ACTION', '新增部门', 'system:department:create', NULL, 9, 0),
    ('system:department', 'ACTION', '修改部门', 'system:department:update', NULL, 10, 0),
    ('system:department', 'ACTION', '修改部门状态', 'system:department:status', NULL, 11, 0),
    ('system:department', 'ACTION', '删除部门', 'system:department:delete', NULL, 12, 0),
    ('system', 'MENU', '角色管理', 'system:role', '/system/roles', 13, 1),
    ('system:role', 'ACTION', '新增角色', 'system:role:create', NULL, 14, 0),
    ('system:role', 'ACTION', '修改角色', 'system:role:update', NULL, 15, 0),
    ('system:role', 'ACTION', '修改角色状态', 'system:role:status', NULL, 16, 0),
    ('system:role', 'ACTION', '删除角色', 'system:role:delete', NULL, 17, 0),
    ('system:role', 'ACTION', '资源授权', 'system:role:resources', NULL, 18, 0),
    ('system', 'MENU', '资源管理', 'system:resource', '/system/resources', 19, 1),
    ('system:resource', 'ACTION', '新增资源', 'system:resource:create', NULL, 20, 0),
    ('system:resource', 'ACTION', '修改资源', 'system:resource:update', NULL, 21, 0),
    ('system:resource', 'ACTION', '修改资源状态', 'system:resource:status', NULL, 22, 0),
    ('system:resource', 'ACTION', '删除资源', 'system:resource:delete', NULL, 23, 0),
    ('system:resource', 'ACTION', '关联接口', 'system:resource:apis', NULL, 24, 0),
    ('system', 'MENU', '接口管理', 'system:api', '/system/apis', 25, 1),
    ('system:api', 'ACTION', '查看接口详情', 'system:api:detail', NULL, 26, 0),
    ('system:api', 'ACTION', '修改接口状态', 'system:api:status', NULL, 27, 0),
    ('system', 'MENU', '任务管理', 'system:task', NULL, 28, 0),
    ('system:task', 'ACTION', '执行任务', 'system:task:run', NULL, 29, 0);

INSERT INTO t_system_resource (
    parent_id, type, name, code, path, icon, sort_order, visible,
    status, created_at, updated_at
)
SELECT 0, definition.type, definition.name, definition.code, definition.path,
       NULL, definition.sort_order, definition.visible, 1, @init_now, @init_now
FROM tmp_system_resource_definition AS definition
WHERE definition.parent_code IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM t_system_resource AS existing WHERE existing.code = definition.code
  );
SET @permission_changed = @permission_changed + ROW_COUNT();

INSERT INTO t_system_resource (
    parent_id, type, name, code, path, icon, sort_order, visible,
    status, created_at, updated_at
)
SELECT parent.id, definition.type, definition.name, definition.code, definition.path,
       NULL, definition.sort_order, definition.visible, 1, @init_now, @init_now
FROM tmp_system_resource_definition AS definition
JOIN t_system_resource AS parent ON parent.code = definition.parent_code
WHERE definition.type = 'MENU'
  AND NOT EXISTS (
      SELECT 1 FROM t_system_resource AS existing WHERE existing.code = definition.code
  );
SET @permission_changed = @permission_changed + ROW_COUNT();

INSERT INTO t_system_resource (
    parent_id, type, name, code, path, icon, sort_order, visible,
    status, created_at, updated_at
)
SELECT parent.id, definition.type, definition.name, definition.code, definition.path,
       NULL, definition.sort_order, definition.visible, 1, @init_now, @init_now
FROM tmp_system_resource_definition AS definition
JOIN t_system_resource AS parent ON parent.code = definition.parent_code
WHERE definition.type = 'ACTION'
  AND NOT EXISTS (
      SELECT 1 FROM t_system_resource AS existing WHERE existing.code = definition.code
  );
SET @permission_changed = @permission_changed + ROW_COUNT();

DROP TEMPORARY TABLE IF EXISTS tmp_resource_api_definition;
CREATE TEMPORARY TABLE tmp_resource_api_definition (
    resource_code VARCHAR(100) NOT NULL,
    api_path VARCHAR(255) NOT NULL,
    PRIMARY KEY (resource_code, api_path)
);

INSERT INTO tmp_resource_api_definition (resource_code, api_path) VALUES
    ('system:employee', '/api/system/employees/page'),
    ('system:employee:create', '/api/system/employees/create'),
    ('system:employee:create', '/api/system/departments/tree'),
    ('system:employee:update', '/api/system/employees/detail'),
    ('system:employee:update', '/api/system/employees/update'),
    ('system:employee:update', '/api/system/departments/tree'),
    ('system:employee:status', '/api/system/employees/status'),
    ('system:employee:delete', '/api/system/employees/delete'),
    ('system:employee:reset-password', '/api/system/employees/reset-password'),
    ('system:employee:roles', '/api/system/roles/page'),
    ('system:employee:roles', '/api/system/employees/role-ids'),
    ('system:employee:roles', '/api/system/employees/roles'),
    ('system:department', '/api/system/departments/tree'),
    ('system:department:create', '/api/system/departments/create'),
    ('system:department:update', '/api/system/departments/detail'),
    ('system:department:update', '/api/system/departments/update'),
    ('system:department:status', '/api/system/departments/status'),
    ('system:department:delete', '/api/system/departments/delete'),
    ('system:role', '/api/system/roles/page'),
    ('system:role:create', '/api/system/roles/create'),
    ('system:role:update', '/api/system/roles/detail'),
    ('system:role:update', '/api/system/roles/update'),
    ('system:role:status', '/api/system/roles/status'),
    ('system:role:delete', '/api/system/roles/delete'),
    ('system:role:resources', '/api/system/resources/tree'),
    ('system:role:resources', '/api/system/roles/resource-ids'),
    ('system:role:resources', '/api/system/roles/resources'),
    ('system:resource', '/api/system/resources/tree'),
    ('system:resource:create', '/api/system/resources/create'),
    ('system:resource:update', '/api/system/resources/detail'),
    ('system:resource:update', '/api/system/resources/update'),
    ('system:resource:status', '/api/system/resources/status'),
    ('system:resource:delete', '/api/system/resources/delete'),
    ('system:resource:apis', '/api/system/apis/page'),
    ('system:resource:apis', '/api/system/resources/api-ids'),
    ('system:resource:apis', '/api/system/resources/apis'),
    ('system:api', '/api/system/apis/page'),
    ('system:api:detail', '/api/system/apis/detail'),
    ('system:api:status', '/api/system/apis/status'),
    ('system:task', '/api/admin/task/list.htm'),
    ('system:task:run', '/api/admin/task/run.htm');

INSERT INTO t_resource_api (resource_id, api_id, created_at)
SELECT resource.id, api.id, @init_now
FROM tmp_resource_api_definition AS definition
JOIN t_system_resource AS resource ON resource.code = definition.resource_code
JOIN t_system_api AS api ON api.path = definition.api_path
WHERE NOT EXISTS (
    SELECT 1
    FROM t_resource_api AS existing
    WHERE existing.resource_id = resource.id AND existing.api_id = api.id
);
SET @permission_changed = @permission_changed + ROW_COUNT();

UPDATE t_system_version
SET version_value = version_value + 1,
    updated_at = @init_now
WHERE version_code = 'permission_snapshot'
  AND @permission_changed > 0;

DROP TEMPORARY TABLE tmp_resource_api_definition;
DROP TEMPORARY TABLE tmp_system_resource_definition;
DROP TEMPORARY TABLE tmp_system_api_definition;

COMMIT;
