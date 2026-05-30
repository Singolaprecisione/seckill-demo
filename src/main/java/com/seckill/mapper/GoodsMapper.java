package com.seckill.mapper;

import com.seckill.entity.Goods;
import com.seckill.entity.SeckillGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsMapper {

    List<Goods> findAllGoods();

    SeckillGoods findSeckillGoodsByGoodsId(@Param("goodsId") Long goodsId);

    int reduceStock(@Param("goodsId") Long goodsId);

    List<SeckillGoods> findAllSeckillGoods();
}
