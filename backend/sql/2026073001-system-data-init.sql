-- 系统基础数据由人工审核后执行，应用启动不自动执行本文件。

SET @init_now = NOW(3);

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
    ('修改接口状态', '/api/system/apis/status', '修改系统接口状态');

INSERT INTO t_system_api (name, path, description, status, created_at, updated_at)
SELECT definition.name, definition.path, definition.description, 1, @init_now, @init_now
FROM tmp_system_api_definition AS definition
WHERE NOT EXISTS (
    SELECT 1 FROM t_system_api AS existing WHERE existing.path = definition.path
);

DROP TEMPORARY TABLE IF EXISTS tmp_system_resource_definition;
CREATE TEMPORARY TABLE tmp_system_resource_definition (
    parent_code VARCHAR(100) NULL,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL,
    path VARCHAR(255) NULL,
    sort_order INT NOT NULL,
    visible TINYINT UNSIGNED NOT NULL,
    api_path VARCHAR(255) NULL,
    PRIMARY KEY (code)
);

INSERT INTO tmp_system_resource_definition (
    parent_code, type, name, code, path, sort_order, visible, api_path
) VALUES
    (NULL, 'DIRECTORY', '系统管理', 'system', NULL, 0, 1, NULL),
    ('system', 'MENU', '员工管理', 'system:employee', '/system/employees', 1, 1, NULL),
    ('system:employee', 'ACTION', '员工分页', 'system:employee:page', NULL, 2, 0, '/api/system/employees/page'),
    ('system:employee', 'ACTION', '员工详情', 'system:employee:detail', NULL, 3, 0, '/api/system/employees/detail'),
    ('system:employee', 'ACTION', '员工角色ID', 'system:employee:role-ids', NULL, 4, 0, '/api/system/employees/role-ids'),
    ('system:employee', 'ACTION', '新增员工', 'system:employee:create', NULL, 5, 0, '/api/system/employees/create'),
    ('system:employee', 'ACTION', '修改员工', 'system:employee:update', NULL, 6, 0, '/api/system/employees/update'),
    ('system:employee', 'ACTION', '修改员工状态', 'system:employee:status', NULL, 7, 0, '/api/system/employees/status'),
    ('system:employee', 'ACTION', '删除员工', 'system:employee:delete', NULL, 8, 0, '/api/system/employees/delete'),
    ('system:employee', 'ACTION', '重置员工密码', 'system:employee:reset-password', NULL, 9, 0, '/api/system/employees/reset-password'),
    ('system:employee', 'ACTION', '保存员工角色', 'system:employee:roles', NULL, 10, 0, '/api/system/employees/roles'),
    ('system', 'MENU', '部门管理', 'system:department', '/system/departments', 11, 1, NULL),
    ('system:department', 'ACTION', '部门树', 'system:department:tree', NULL, 12, 0, '/api/system/departments/tree'),
    ('system:department', 'ACTION', '部门详情', 'system:department:detail', NULL, 13, 0, '/api/system/departments/detail'),
    ('system:department', 'ACTION', '新增部门', 'system:department:create', NULL, 14, 0, '/api/system/departments/create'),
    ('system:department', 'ACTION', '修改部门', 'system:department:update', NULL, 15, 0, '/api/system/departments/update'),
    ('system:department', 'ACTION', '修改部门状态', 'system:department:status', NULL, 16, 0, '/api/system/departments/status'),
    ('system:department', 'ACTION', '删除部门', 'system:department:delete', NULL, 17, 0, '/api/system/departments/delete'),
    ('system', 'MENU', '角色管理', 'system:role', '/system/roles', 18, 1, NULL),
    ('system:role', 'ACTION', '角色分页', 'system:role:page', NULL, 19, 0, '/api/system/roles/page'),
    ('system:role', 'ACTION', '角色详情', 'system:role:detail', NULL, 20, 0, '/api/system/roles/detail'),
    ('system:role', 'ACTION', '角色资源ID', 'system:role:resource-ids', NULL, 21, 0, '/api/system/roles/resource-ids'),
    ('system:role', 'ACTION', '新增角色', 'system:role:create', NULL, 22, 0, '/api/system/roles/create'),
    ('system:role', 'ACTION', '修改角色', 'system:role:update', NULL, 23, 0, '/api/system/roles/update'),
    ('system:role', 'ACTION', '修改角色状态', 'system:role:status', NULL, 24, 0, '/api/system/roles/status'),
    ('system:role', 'ACTION', '删除角色', 'system:role:delete', NULL, 25, 0, '/api/system/roles/delete'),
    ('system:role', 'ACTION', '保存角色资源', 'system:role:resources', NULL, 26, 0, '/api/system/roles/resources'),
    ('system', 'MENU', '资源管理', 'system:resource', '/system/resources', 27, 1, NULL),
    ('system:resource', 'ACTION', '资源树', 'system:resource:tree', NULL, 28, 0, '/api/system/resources/tree'),
    ('system:resource', 'ACTION', '资源详情', 'system:resource:detail', NULL, 29, 0, '/api/system/resources/detail'),
    ('system:resource', 'ACTION', '新增资源', 'system:resource:create', NULL, 30, 0, '/api/system/resources/create'),
    ('system:resource', 'ACTION', '修改资源', 'system:resource:update', NULL, 31, 0, '/api/system/resources/update'),
    ('system:resource', 'ACTION', '修改资源状态', 'system:resource:status', NULL, 32, 0, '/api/system/resources/status'),
    ('system:resource', 'ACTION', '删除资源', 'system:resource:delete', NULL, 33, 0, '/api/system/resources/delete'),
    ('system:resource', 'ACTION', '资源接口ID', 'system:resource:api-ids', NULL, 34, 0, '/api/system/resources/api-ids'),
    ('system:resource', 'ACTION', '保存资源接口', 'system:resource:apis', NULL, 35, 0, '/api/system/resources/apis'),
    ('system', 'MENU', '接口管理', 'system:api', '/system/apis', 36, 1, NULL),
    ('system:api', 'ACTION', '接口分页', 'system:api:page', NULL, 37, 0, '/api/system/apis/page'),
    ('system:api', 'ACTION', '接口详情', 'system:api:detail', NULL, 38, 0, '/api/system/apis/detail'),
    ('system:api', 'ACTION', '修改接口状态', 'system:api:status', NULL, 39, 0, '/api/system/apis/status');

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

INSERT INTO t_resource_api (resource_id, api_id, created_at)
SELECT resource.id, api.id, @init_now
FROM tmp_system_resource_definition AS definition
JOIN t_system_resource AS resource ON resource.code = definition.code
JOIN t_system_api AS api ON api.path = definition.api_path
WHERE definition.api_path IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM t_resource_api AS existing
      WHERE existing.resource_id = resource.id AND existing.api_id = api.id
  );

DROP TEMPORARY TABLE tmp_system_resource_definition;
DROP TEMPORARY TABLE tmp_system_api_definition;
