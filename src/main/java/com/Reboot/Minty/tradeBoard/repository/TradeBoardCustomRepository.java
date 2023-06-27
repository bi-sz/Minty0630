package com.Reboot.Minty.tradeBoard.repository;

import com.Reboot.Minty.categories.dto.SubCategoryDto;
import com.Reboot.Minty.tradeBoard.dto.TradeBoardDto;
import com.Reboot.Minty.tradeBoard.dto.TradeBoardSearchDto;
import com.Reboot.Minty.tradeBoard.entity.QTradeBoard;
import com.Reboot.Minty.utils.OrderByNull;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.thymeleaf.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Repository
public class TradeBoardCustomRepository {

    private final JPAQueryFactory queryFactory;

    public TradeBoardCustomRepository(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    private BooleanExpression searchByLike(String searchQuery) {
        if (!searchQuery.isEmpty()) {
            if (searchQuery.startsWith("@")) {
                String nickNameQuery = searchQuery.substring(1); // Remove the "@" symbol
                return QTradeBoard.tradeBoard.user.nickName.like("%" + nickNameQuery + "%");
            } else {
                return QTradeBoard.tradeBoard.title.like("%" + searchQuery + "%")
                        .or(QTradeBoard.tradeBoard.userLocation.address.like("%" + searchQuery + "%"))
                        .or(QTradeBoard.tradeBoard.user.nickName.like("%" + searchQuery + "%"));
            }
        }
        return null;
    }

    private OrderSpecifier<?> sortingItems(String sortBy) {
        Order order = Order.DESC;
        if (Objects.isNull(sortBy)) {
            return OrderByNull.getDefault();
        }
        if (StringUtils.equals("itemDesc", sortBy)) {
            return new OrderSpecifier<>(order, QTradeBoard.tradeBoard.modifiedDate);
        }
        if (StringUtils.equals("priceAsc", sortBy)) {
            return new OrderSpecifier<>(Order.ASC, QTradeBoard.tradeBoard.price);
        }
        if (StringUtils.equals("priceDesc", sortBy)) {
            return new OrderSpecifier<>(order, QTradeBoard.tradeBoard.price);
        }
        return null;
    }

    private BooleanExpression searchByCategory(Long subCategoryId) {
        if (subCategoryId != null) {
            return QTradeBoard.tradeBoard.subCategory.id.eq(subCategoryId);
        }
        return null;
    }

    private BooleanExpression searchPriceByBetween(int minPrice, int maxPrice) {
        if (minPrice > 0 && maxPrice > 0) {
            return QTradeBoard.tradeBoard.price.between(minPrice, maxPrice);
        } else if (minPrice > 0) {
            return QTradeBoard.tradeBoard.price.goe(minPrice);
        } else if (maxPrice > 0) {
            return QTradeBoard.tradeBoard.price.loe(maxPrice);
        }
        return null;
    }

    public Page<TradeBoardDto> getTradeBoardBy(TradeBoardSearchDto searchDto, Pageable pageable) {
        QTradeBoard qtb = QTradeBoard.tradeBoard;
        BooleanExpression searchExpression = qtb.isNotNull(); // Initial expression to start with

        // Add search conditions
        BooleanExpression likeExpression = searchByLike(searchDto.getSearchQuery());
        if (likeExpression != null) {
            searchExpression = searchExpression.and(likeExpression);
        }

        BooleanExpression categoryExpression = searchByCategory(searchDto.getSubCategoryId());
        if (categoryExpression != null) {
            searchExpression = searchExpression.and(categoryExpression);
        }

        BooleanExpression priceExpression = searchPriceByBetween(searchDto.getMinPrice(), searchDto.getMaxPrice());
        if (priceExpression != null) {
            searchExpression = searchExpression.and(priceExpression);
        }
        System.out.println(searchExpression);

        // Add sorting
        OrderSpecifier<?> orderSpecifier = sortingItems(searchDto.getSortBy());

        List<TradeBoardDto> tradeBoards = queryFactory.select(Projections.constructor(TradeBoardDto.class,
                        qtb.id, qtb.price, qtb.title, qtb.createdDate, qtb.modifiedDate, qtb.interesting, qtb.visit_count, qtb.thumbnail,
                        qtb.topCategory.id, qtb.topCategory.name, qtb.subCategory.id, qtb.subCategory.name,
                        qtb.user.id, qtb.user.email, qtb.user.nickName,
                        qtb.userLocation.id, qtb.userLocation.address,
                        qtb.status))
                .from(qtb)
                .where(searchExpression)
                .orderBy(orderSpecifier)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = queryFactory.selectFrom(qtb).where(searchExpression).fetchCount();

        return new PageImpl<>(tradeBoards, pageable, total);
    }

}
