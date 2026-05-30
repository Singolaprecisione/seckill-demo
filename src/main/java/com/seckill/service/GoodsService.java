package com.seckill.service;

import com.seckill.entity.Goods;
import com.seckill.entity.SeckillGoods;
import com.seckill.mapper.GoodsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoodsService {

    @Autowired
    private GoodsMapper goodsMapper;

    public List<Goods> getAllGoods() {
        return goodsMapper.findAllGoods();
    }

    public SeckillGoods getSeckillGoodsDetail(Long goodsId) {
        return goodsMapper.findSeckillGoodsByGoodsId(goodsId);
    }

    public int reduceStock(Long goodsId) {
        return goodsMapper.reduceStock(goodsId);
    }

    public List<SeckillGoods> getAllSeckillGoods() {
        return goodsMapper.findAllSeckillGoods();
    }
}
