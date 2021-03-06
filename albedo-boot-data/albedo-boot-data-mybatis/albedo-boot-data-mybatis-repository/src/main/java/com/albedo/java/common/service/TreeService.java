package com.albedo.java.common.service;

import com.albedo.java.common.data.persistence.BaseEntity;
import com.albedo.java.common.data.persistence.repository.TreeRepository;
import com.albedo.java.common.domain.base.TreeEntity;
import com.albedo.java.modules.sys.domain.Area;
import com.albedo.java.util.PublicUtil;
import com.albedo.java.util.base.Assert;
import com.albedo.java.util.exception.RuntimeMsgException;
import com.albedo.java.vo.sys.query.TreeQuery;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Transactional
public abstract class TreeService<Repository extends TreeRepository<T, PK>, T extends TreeEntity, PK extends Serializable>
        extends DataService<Repository, T, PK> {
    /**
     * 逻辑删除
     *
     * @param id
     * @param likeParentIds
     * @return
     */
    public void deleteById(PK id, String likeParentIds, String lastModifiedBy) {
        operateStatusById(id, likeParentIds, BaseEntity.FLAG_DELETE, lastModifiedBy);
    }

    public void operateStatusById(PK id, String likeParentIds, Integer status, String lastModifiedBy) {
        T entity = repository.findOneByIdOrParentIdsLike(id, likeParentIds);
        Assert.assertNotNull(entity, "无法查询到对象信息");
        entity.setStatus(status);
//        entity.setLastModifiedBy(lastModifiedBy);
//        entity.setLastModifiedDate(PublicUtil.getCurrentDate());
        repository.updateIgnoreNull(entity);

    }

    public T save(T entity) {
        String oldParentIds = entity.getParentIds(); // 获取修改前的parentIds，用于更新子节点的parentIds
        if (entity.getParentId() != null) {
            T parent = repository.findOneById(entity.getParentId());
            if (parent == null || PublicUtil.isEmpty(parent.getId()))
                throw new RuntimeMsgException("无法获取模块的父节点，插入失败");
            if (parent != null) {
                parent.setLeaf(false);
//                checkSave(parent);
                repository.save(parent);
            }
            entity.setParentIds(PublicUtil.toAppendStr(parent.getParentIds(), parent.getId(), ","));
        }

        if (PublicUtil.isNotEmpty(entity.getId())) {
            Long count = repository.countByParentId(entity.getId());
            entity.setLeaf(count == null || count == 0 ? true : false);
        } else {
            entity.setLeaf(true);
        }
//        checkSave(entity);
        entity = repository.save(entity);
        // 更新子节点 parentIds
        List<T> list = repository.findAllByParentIdsLike(PublicUtil.toAppendStr("%,", entity.getId(), ",%"));
        for (T e : list) {
            e.setParentIds(e.getParentIds().replace(oldParentIds, entity.getParentIds()));
        }
        repository.save(list);
        log.debug("Save Information for T: {}", entity);
        return entity;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findTreeData(TreeQuery query) {
        String extId = query != null ? query.getExtId() : null, all = query != null ? query.getAll() : null;
        List<Map<String, Object>> mapList = Lists.newArrayList();
        List<T> list = repository.findAllByStatusNot(BaseEntity.FLAG_DELETE);
        for (T e : list) {
            if ((PublicUtil.isEmpty(extId)
                    || PublicUtil.isEmpty(e.getParentIds()) || (PublicUtil.isNotEmpty(extId) && !extId.equals(e.getId()) && e.getParentIds() != null && e.getParentIds().indexOf("," + extId + ",") == -1))
                    && (all != null || (all == null && BaseEntity.FLAG_NORMAL.equals(e.getStatus())))) {
                Map<String, Object> map = Maps.newHashMap();
                map.put("id", e.getId());
                map.put("pId", PublicUtil.isEmpty(e.getParentId()) ? "0" : e.getParentId());
                map.put("name", e.getName());
                map.put("pIds", e.getParentIds());
                mapList.add(map);
            }
        }
        return mapList;
    }

    @Transactional(readOnly = true)
    public T findTopByParentId(String parentId) {
        List<T> tempList = repository.findTop1ByParentIdAndStatusNotOrderBySortDesc(parentId, BaseEntity.FLAG_DELETE);
        return PublicUtil.isNotEmpty(tempList) ? tempList.get(0) : null;
    }

    @Transactional(readOnly = true)
    public Long countTopByParentId(String parentId) {
        return repository.countByParentIdAndStatusNot(parentId, Area.FLAG_DELETE);
    }


}
