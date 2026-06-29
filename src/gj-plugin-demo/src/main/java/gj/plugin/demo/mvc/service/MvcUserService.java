package gj.plugin.demo.mvc.service;

import gj.plugin.demo.mvc.dto.MvcUserCreateRequest;
import gj.plugin.demo.mvc.dto.MvcUserResponse;

import java.util.List;

public interface MvcUserService {

    List<MvcUserResponse> getList();

    MvcUserResponse getById(Integer id);

    boolean create(MvcUserCreateRequest request);

    boolean update(Integer id, MvcUserCreateRequest request);

    boolean delete(Integer id);

    List<MvcUserResponse> search(String keyword);
}
