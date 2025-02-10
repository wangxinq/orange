package com.wangxq.cloud.service.impl;

import com.wangxq.cloud.apis.AccountFeignApi;
import com.wangxq.cloud.apis.StorageFeignApi;
import com.wangxq.cloud.entities.Order;
import com.wangxq.cloud.mapper.OrderMapper;
import com.wangxq.cloud.service.OrderService;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Resource
    private OrderMapper orderMapper;
    @Resource//订单微服务通过OpenFeign去调用库存微服务
    private StorageFeignApi storageFeignApi;
    @Resource//订单微服务通过OpenFeign去调用账户微服务
    private AccountFeignApi accountFeignApi;
    @Override
    @GlobalTransactional
    public void create(Order order) {
        String xid = RootContext.getXID();
        log.info("--------开始新建订单"+"\t"+"xid:"+xid);
        //订单新建时默认初始状态是0
        order.setStatus(0);
        int result = orderMapper.insertSelective(order);
        //如果插入成功后，获得插入mysql的实体对象
        Order orderFromDB = null;
        if(result > 0){
            //1.从mysql中查出来刚插入的记录
            orderFromDB = orderMapper.selectOne(order);
            log.info("-------> 新建订单成功");
            //2.扣减库存
            log.info("-------> 订单微服务开始调用Storage库存，做扣减count");
            storageFeignApi.decrease(order.getProductId(),order.getCount());
            log.info("-------> 订单微服务结束调用Storage库存，做扣减完成");
            System.out.println();
            //3. 扣减账号余额
            accountFeignApi.decrease(order.getUserId(),order.getMoney());
            log.info("-------> 订单微服务结束调用Account账号，做扣减完成");
            System.out.println();
            //4.修改订单状态，订单状态为1时表示已完结
            log.info("-------> 修改订单状态");
            orderFromDB.setStatus(1);

            Example whereCondition = new Example(Order.class);
            Example.Criteria criteria = whereCondition.createCriteria();
            criteria.andEqualTo("userId",orderFromDB.getUserId());
            criteria.andEqualTo("status",0);
            int updateResult = orderMapper.updateByExampleSelective(orderFromDB,whereCondition);
            log.info("-------> 修改订单状态完成"+"\t"+updateResult);
            log.info("-------> orderFromDB info: "+orderFromDB);
        }
        System.out.println();
        log.info("==================>结束新建订单"+"\t"+"xid_order:" +xid);
    }
}
