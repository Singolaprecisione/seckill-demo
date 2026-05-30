package com.seckill.mapper;

import com.seckill.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper {

    int insertOrder(OrderInfo orderInfo);

    int countByUserAndGoods(@Param("userId") Long userId, @Param("goodsId") Long goodsId);
}
