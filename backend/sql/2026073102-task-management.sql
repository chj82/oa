-- 任务管理接口及权限资源，需在 2026073101-permission-cache-redesign.sql 后执行。
SET @task_now = NOW(3);
SET @task_changed = 0;

INSERT INTO t_system_api (name, path, description, status, created_at, updated_at)
SELECT '任务列表', '/api/admin/task/list.htm', '查询后台任务列表', 1, @task_now, @task_now
WHERE NOT EXISTS (
    SELECT 1 FROM t_system_api WHERE path = '/api/admin/task/list.htm'
);
SET @task_changed = @task_changed + ROW_COUNT();

INSERT INTO t_system_api (name, path, description, status, created_at, updated_at)
SELECT '执行任务', '/api/admin/task/run.htm', '手动执行后台任务', 1, @task_now, @task_now
WHERE NOT EXISTS (
    SELECT 1 FROM t_system_api WHERE path = '/api/admin/task/run.htm'
);
SET @task_changed = @task_changed + ROW_COUNT();

INSERT INTO t_system_resource (
    parent_id, type, name, code, path, icon, sort_order, visible,
    status, created_at, updated_at
)
SELECT system_resource.id, 'MENU', '任务管理', 'system:task', NULL, NULL, 40, 0,
       1, @task_now, @task_now
FROM t_system_resource AS system_resource
WHERE system_resource.code = 'system'
  AND NOT EXISTS (
      SELECT 1 FROM t_system_resource WHERE code = 'system:task'
  );
SET @task_changed = @task_changed + ROW_COUNT();

INSERT INTO t_system_resource (
    parent_id, type, name, code, path, icon, sort_order, visible,
    status, created_at, updated_at
)
SELECT task_resource.id, 'ACTION', '任务列表', 'system:task:list', NULL, NULL, 41, 0,
       1, @task_now, @task_now
FROM t_system_resource AS task_resource
WHERE task_resource.code = 'system:task'
  AND NOT EXISTS (
      SELECT 1 FROM t_system_resource WHERE code = 'system:task:list'
  );
SET @task_changed = @task_changed + ROW_COUNT();

INSERT INTO t_system_resource (
    parent_id, type, name, code, path, icon, sort_order, visible,
    status, created_at, updated_at
)
SELECT task_resource.id, 'ACTION', '执行任务', 'system:task:run', NULL, NULL, 42, 0,
       1, @task_now, @task_now
FROM t_system_resource AS task_resource
WHERE task_resource.code = 'system:task'
  AND NOT EXISTS (
      SELECT 1 FROM t_system_resource WHERE code = 'system:task:run'
  );
SET @task_changed = @task_changed + ROW_COUNT();

INSERT INTO t_resource_api (resource_id, api_id, created_at)
SELECT resource.id, api.id, @task_now
FROM t_system_resource AS resource
JOIN t_system_api AS api ON api.path = '/api/admin/task/list.htm'
WHERE resource.code = 'system:task:list'
  AND NOT EXISTS (
      SELECT 1
      FROM t_resource_api AS existing
      WHERE existing.resource_id = resource.id AND existing.api_id = api.id
  );
SET @task_changed = @task_changed + ROW_COUNT();

INSERT INTO t_resource_api (resource_id, api_id, created_at)
SELECT resource.id, api.id, @task_now
FROM t_system_resource AS resource
JOIN t_system_api AS api ON api.path = '/api/admin/task/run.htm'
WHERE resource.code = 'system:task:run'
  AND NOT EXISTS (
      SELECT 1
      FROM t_resource_api AS existing
      WHERE existing.resource_id = resource.id AND existing.api_id = api.id
  );
SET @task_changed = @task_changed + ROW_COUNT();

UPDATE t_system_version
SET version_value = version_value + 1,
    updated_at = @task_now
WHERE version_code = 'permission_snapshot'
  AND @task_changed > 0;
