package gj.data.service;

import gj.data.dao.TestMapper;
import gj.data.model.Test;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestService {
    private final TestMapper testMapper;

    public List<Test> getAllTests() {
        return testMapper.selectList(null);
    }

    public Test getTestById(Long id) {
        return testMapper.selectById(id);
    }

    public void saveTest(Test test) {
        testMapper.insert(test);
    }

    public void updateTest(Test test) {
        testMapper.updateById(test);
    }

    public void deleteTest(Long id) {
        testMapper.deleteById(id);
    }
}