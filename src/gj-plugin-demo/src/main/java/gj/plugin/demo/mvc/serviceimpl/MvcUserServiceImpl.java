package gj.plugin.demo.mvc.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import gj.plugin.demo.dao.MvcUserMapper;
import gj.plugin.demo.mvc.dto.MvcUserCreateRequest;
import gj.plugin.demo.mvc.dto.MvcUserResponse;
import gj.plugin.demo.mvc.model.MvcUser;
import gj.plugin.demo.mvc.service.MvcUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MvcUserServiceImpl implements MvcUserService {

    private final MvcUserMapper mvcUserMapper;
    private final ModelMapper modelMapper;

    @Override
    public List<MvcUserResponse> getList() {
        return mvcUserMapper.selectList(Wrappers.lambdaQuery())
                .stream()
                .map(u -> modelMapper.map(u, MvcUserResponse.class))
                .toList();
    }

    @Override
    public MvcUserResponse getById(Integer id) {
        MvcUser user = mvcUserMapper.selectById(id);
        return modelMapper.map(user, MvcUserResponse.class);
    }

    @Override
    public boolean create(MvcUserCreateRequest request) {
        MvcUser user = modelMapper.map(request, MvcUser.class);
        return mvcUserMapper.insert(user) > 0;
    }

    @Override
    public boolean update(Integer id, MvcUserCreateRequest request) {
        MvcUser user = modelMapper.map(request, MvcUser.class);
        user.setId(id);
        return mvcUserMapper.updateById(user) > 0;
    }

    @Override
    public boolean delete(Integer id) {
        return mvcUserMapper.deleteById(id) > 0;
    }

    @Override
    public List<MvcUserResponse> search(String keyword) {
        LambdaQueryWrapper<MvcUser> wrapper = Wrappers.lambdaQuery();
        wrapper.like(MvcUser::getName, keyword).or().like(MvcUser::getEmail, keyword);
        return mvcUserMapper.selectList(wrapper)
                .stream()
                .map(u -> modelMapper.map(u, MvcUserResponse.class))
                .toList();
    }
}
