CREATE TABLE t_department (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父部门ID，根部门为0',
    name VARCHAR(100) NOT NULL COMMENT '部门名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_department_parent_name (parent_id, name),
    KEY idx_department_parent_sort (parent_id, sort_order),
    KEY idx_department_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

CREATE TABLE t_employee (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '员工ID',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    name VARCHAR(100) NOT NULL COMMENT '员工姓名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希',
    phone VARCHAR(32) NULL COMMENT '手机号',
    email VARCHAR(255) NULL COMMENT '邮箱',
    department_id BIGINT UNSIGNED NOT NULL COMMENT '所属部门ID',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    is_superuser TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否超级管理员：0否，1是',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_employee_username (username),
    UNIQUE KEY udx_employee_phone (phone),
    UNIQUE KEY udx_employee_email (email),
    KEY idx_employee_department_status (department_id, status),
    KEY idx_employee_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工';

CREATE TABLE t_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    code VARCHAR(64) NOT NULL COMMENT '角色编码',
    name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(500) NULL COMMENT '角色描述',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_role_code (code),
    UNIQUE KEY udx_role_name (name),
    KEY idx_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE t_system_resource (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父资源ID，根资源为0',
    type VARCHAR(16) NOT NULL COMMENT '资源类型：DIRECTORY、MENU、ACTION',
    name VARCHAR(100) NOT NULL COMMENT '资源名称',
    code VARCHAR(100) NULL COMMENT '菜单或按钮资源编码',
    path VARCHAR(255) NULL COMMENT '前端菜单路由',
    icon VARCHAR(100) NULL COMMENT '图标名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    visible TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否可见：0否，1是',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_system_resource_code (code),
    UNIQUE KEY udx_system_resource_path (path),
    KEY idx_system_resource_parent_sort (parent_id, sort_order),
    KEY idx_system_resource_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统资源';

CREATE TABLE t_system_api (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '接口ID',
    name VARCHAR(100) NOT NULL COMMENT '接口名称',
    path VARCHAR(255) NOT NULL COMMENT 'Spring MVC路由模板，也是接口资源标识',
    description VARCHAR(500) NULL COMMENT '接口描述',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_system_api_path (path),
    KEY idx_system_api_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统接口目录';

CREATE TABLE t_employee_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '员工角色关联ID',
    employee_id BIGINT UNSIGNED NOT NULL COMMENT '员工ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_employee_role_employee_role (employee_id, role_id),
    KEY idx_employee_role_role_employee (role_id, employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工角色关联';

CREATE TABLE t_role_resource (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色资源关联ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    resource_id BIGINT UNSIGNED NOT NULL COMMENT '资源ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_role_resource_role_resource (role_id, resource_id),
    KEY idx_role_resource_resource_role (resource_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色资源关联';

CREATE TABLE t_resource_api (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资源接口关联ID',
    resource_id BIGINT UNSIGNED NOT NULL COMMENT '资源ID',
    api_id BIGINT UNSIGNED NOT NULL COMMENT '接口ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_resource_api_resource_api (resource_id, api_id),
    KEY idx_resource_api_api_resource (api_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源接口关联';
