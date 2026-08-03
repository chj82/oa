package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oa.common.model.system.cache.ResourcePermissionSnapshot;
import com.oa.dao.system.ResourceApiMapper;
import com.oa.dao.system.SystemApiMapper;
import com.oa.dao.system.SystemResourceMapper;
import com.oa.dao.system.SystemVersionMapper;
import com.oa.entity.system.ResourceApiEntity;
import com.oa.entity.system.SystemApiEntity;
import com.oa.entity.system.SystemResourceEntity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 资源权限快照一致性加载测试。 */
class PermissionSnapshotLoaderTest {

  /** 加载器只发布祖先链有效的资源及其启用接口。 */
  @Test
  void 加载有效资源和启用接口() {
    SystemVersionMapper versionMapper = mock(SystemVersionMapper.class);
    SystemResourceMapper resourceMapper = mock(SystemResourceMapper.class);
    ResourceApiMapper relationMapper = mock(ResourceApiMapper.class);
    SystemApiMapper apiMapper = mock(SystemApiMapper.class);
    when(versionMapper.selectVersion("permission_snapshot")).thenReturn(8L);
    when(resourceMapper.selectAllOrdered())
        .thenReturn(
            List.of(
                resource(100L, 0L, "DIRECTORY", 1),
                resource(101L, 100L, "MENU", 1),
                resource(102L, 101L, "ACTION", 1),
                resource(200L, 0L, "DIRECTORY", 0),
                resource(201L, 200L, "MENU", 1)));
    when(relationMapper.selectAllRelations())
        .thenReturn(List.of(relation(101L, 1L), relation(102L, 2L), relation(201L, 1L)));
    when(apiMapper.selectAllEnabled()).thenReturn(List.of(api(1L, "/api/system/employee/page", 1)));

    ResourcePermissionSnapshot snapshot =
        new PermissionSnapshotLoader(versionMapper, resourceMapper, relationMapper, apiMapper)
            .load();

    assertEquals(8L, snapshot.getVersion());
    assertTrue(snapshot.getNodes().containsKey(100L));
    assertEquals(Set.of("/api/system/employee/page"), snapshot.getNodes().get(101L).getApiPaths());
    assertFalse(snapshot.getNodes().containsKey(200L));
    assertFalse(snapshot.getNodes().containsKey(201L));
    assertFalse(snapshot.getAllEnabledApiPaths().contains("/api/disabled"));
  }

  /** 发布后的快照集合不可被请求线程修改。 */
  @Test
  void 快照集合不可修改() {
    SystemVersionMapper versionMapper = mock(SystemVersionMapper.class);
    SystemResourceMapper resourceMapper = mock(SystemResourceMapper.class);
    ResourceApiMapper relationMapper = mock(ResourceApiMapper.class);
    SystemApiMapper apiMapper = mock(SystemApiMapper.class);
    when(versionMapper.selectVersion("permission_snapshot")).thenReturn(1L);
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(resource(100L, 0L, "MENU", 1)));
    when(relationMapper.selectAllRelations()).thenReturn(List.of(relation(100L, 1L)));
    when(apiMapper.selectAllEnabled()).thenReturn(List.of(api(1L, "/api/enabled", 1)));

    ResourcePermissionSnapshot snapshot =
        new PermissionSnapshotLoader(versionMapper, resourceMapper, relationMapper, apiMapper)
            .load();

    assertThrows(UnsupportedOperationException.class, () -> snapshot.getNodes().clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> snapshot.getNodes().get(100L).getApiPaths().add("/api/other"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> snapshot.getAllEnabledApiPaths().add("/api/other"));
  }

  private SystemResourceEntity resource(long id, long parentId, String type, int status) {
    SystemResourceEntity resource = new SystemResourceEntity();
    resource.setId(id);
    resource.setParentId(parentId);
    resource.setType(type);
    resource.setName("资源" + id);
    resource.setCode("RESOURCE_" + id);
    resource.setPath("/resource/" + id);
    resource.setIcon("menu");
    resource.setSortOrder((int) id);
    resource.setVisible(1);
    resource.setStatus(status);
    return resource;
  }

  private ResourceApiEntity relation(long resourceId, long apiId) {
    ResourceApiEntity relation = new ResourceApiEntity();
    relation.setResourceId(resourceId);
    relation.setApiId(apiId);
    return relation;
  }

  private SystemApiEntity api(long id, String path, int status) {
    SystemApiEntity api = new SystemApiEntity();
    api.setId(id);
    api.setPath(path);
    api.setStatus(status);
    return api;
  }
}
