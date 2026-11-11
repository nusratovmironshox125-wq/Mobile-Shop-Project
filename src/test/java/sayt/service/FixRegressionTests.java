package sayt.service;

import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import sayt.dto.catalog.*;
import sayt.exception.Problem;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/rms_test",
    "spring.datasource.username=rms_test","spring.datasource.password=rms-test-placeholder",
    "spring.flyway.url=jdbc:postgresql://127.0.0.1:55432/rms_test",
    "spring.flyway.user=rms_test","spring.flyway.password=rms-test-placeholder"
})
@EnabledIfEnvironmentVariable(named="RMS_TEST_ONLY",matches="yes")
@Transactional
class FixRegressionTests {
    @Autowired JdbcClient db;
    @Autowired CatalogService catalog;
    @Autowired ProductService products;
    @Autowired LeadService leads;
    @Autowired LeadReadService reads;
    @Autowired AuditService audit;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;

    long actor(String role) {
        return db.sql("insert into admin_user(login,name,password_hash,role) values (?,'Test','not-a-login-hash',?) returning id")
            .params("qa-"+UUID.randomUUID(),role).query(Long.class).single();
    }
    long product(String kind,String name) {
        long id=db.sql("""
            insert into product(category_id,brand,name,slug,status)
            select id,'QA',?,?,'PUBLISHED' from category where kind=? returning id
            """).params(name,UUID.randomUUID().toString(),kind).query(Long.class).single();
        db.sql("insert into product_variant(product_id,sku,stock,price,color,ram_gb,storage_gb) values (?,?,'IN_STOCK',2000000,'Black',8,128)")
            .params(id,UUID.randomUUID().toString()).update();
        return id;
    }
    VariantEdit input(String sku,String extra) {
        return json.readValue("{\"sku\":\""+sku+"\",\"price\":2200000,\"stock\":\"IN_STOCK\",\"installment\":false"+extra+"}",VariantEdit.class);
    }
    @Test void publicPaginationCountsModelsAndFiltersBeforeOffset() {
        String tag="Boundary"+UUID.randomUUID();
        for(int count=0;count<=21;count++) {
            if(count>0) product("PHONE",tag+count);
            if(Set.of(0,1,10,11,20,21).contains(count)) {
                for(int page=0;page<4;page++) {
                    var list=catalog.list(null,tag,null,null,false,"new",page,10,null);
                    assertEquals(count,list.totalElements());
                    assertEquals((count+9)/10,list.totalPages());
                    assertEquals(Math.max(0,Math.min(10,count-page*10)),list.items().size());
                    assertEquals((page+1)*10<count,list.hasNext());
                }
            }
        }
        var list=catalog.list(null,tag+"1",null,null,false,"new",0,10,null);
        assertEquals(11,list.totalElements()); // 1,10..19
        long id=list.items().getFirst().id();
        db.sql("insert into product_variant(product_id,sku,stock,price) values (?,?,'IN_STOCK',1000000)")
            .params(id,UUID.randomUUID().toString()).update();
        assertEquals(21,catalog.list(null,tag,null,null,false,"new",0,10,null).totalElements());
        assertEquals(0,catalog.list(null,"' OR 1=1 --",null,null,false,"new",0,10,null).totalElements());
    }
    @Test void categoryRulesApplyToDirectDtoAndPreventChangingTechnicalType() {
        long owner=actor("OWNER");
        long phone=product("PHONE","Phone");
        long laptop=product("LAPTOP","Laptop");
        assertThrows(Problem.class,()->products.saveVariant(phone,null,input("A",",\"gpu\":\"Alien\""),owner));
        assertThrows(Problem.class,()->products.saveVariant(laptop,null,input("B",",\"sim\":\"Alien\""),owner));
        var good=products.saveVariant(laptop,null,input(" test-Code ",",\"storageType\":\"SSD\",\"gpu\":\"Test GPU\""),owner);
        assertEquals("TEST-CODE",good.sku());
        assertEquals(1,products.adminProducts(0,"test-code").getTotalElements());
        var p=catalog.adminDetail(phone).product();
        long category=db.sql("select id from category where kind='LAPTOP'").query(Long.class).single();
        assertThrows(Problem.class,()->products.saveProduct(phone,new ProductEdit(category,p.brand(),p.name(),null,"","PUBLISHED",p.version()),owner));
    }
    @Test void autoLinkRemainsStableAndAuditHasSnapshotAndPriceDiff() {
        long owner=actor("OWNER");
        long cat=db.sql("select id from category where kind='PHONE'").query(Long.class).single();
        var p=products.saveProduct(null,new ProductEdit(cat,"测试","测试",null,"","DRAFT",null),owner);
        assertEquals("product-"+p.id(),p.slug());
        var renamed=products.saveProduct(p.id(),new ProductEdit(cat,"Changed","Renamed","ignored","","DRAFT",p.version()),owner);
        assertEquals(p.slug(),renamed.slug());
        var v=products.saveVariant(p.id(),null,input("AUDIT-"+owner,""),owner);
        var changed=input(v.sku(),",\"version\":"+v.version());
        var cheaper=new VariantEdit(changed.sku(),null,null,null,null,null,null,null,null,null,null,null,null,
            new BigDecimal("2100000"),"IN_STOCK",false,null,null,null,null,changed.version(),null,false);
        products.saveVariant(p.id(),v.id(),cheaper,owner);
        var row=audit.list(0,null,"VARIANT_SAVED","variant:"+v.id(),null,null).items().getFirst();
        assertNotNull(row.get("actor_username"));
        assertEquals("OWNER",row.get("actor_role"));
        var diff=json.readTree(row.get("changes").toString()).get("price");
        assertEquals(2200000,diff.get("before").asInt());
        assertEquals(2100000,diff.get("after").asInt());
        String username=row.get("actor_username").toString();
        db.sql("update admin_user set login=?,enabled=false where id=?").params("renamed-"+owner,owner).update();
        assertEquals(username,audit.list(0,null,"VARIANT_SAVED","variant:"+v.id(),null,null).items().getFirst().get("actor_username"));
    }
    @Test void readReceiptsArePerAdminAndDoNotChangeBusinessStatus() {
        long a=actor("OWNER"), b=actor("ADMIN");
        long product=product("PHONE","Read test");
        long variant=db.sql("select id from product_variant where product_id=?").param(product).query(Long.class).single();
        long lead=db.sql("""
            insert into lead(public_number,idempotency_key,request_hash,product_id,variant_id,product_name,sku,
              variant_label,price,installment,status,privacy_version)
            values (?,?,'test',?,?,'Test','TEST','Color: Black',2000000,false,'NEW','test') returning id
            """).params(UUID.randomUUID(),UUID.randomUUID(),product,variant).query(Long.class).single();
        long before=reads.updates(a).unreadCount();
        leads.detail(lead); // GET alone must not change read state.
        assertEquals(before,reads.updates(a).unreadCount());
        reads.mark(lead,a); reads.mark(lead,a);
        assertEquals(before-1,reads.updates(a).unreadCount());
        assertEquals(before,reads.updates(b).unreadCount());
        assertEquals("NEW",leads.detail(lead).lead().status());
        assertNotNull(leads.list(null,null,null,null,0,a).items().stream().filter(l->l.id()==lead).findFirst().orElseThrow().readAt());
    }
    @Test void rollbackDoesNotLeaveSuccessfulAudit() {
        String label="rollback-"+UUID.randomUUID();
        var tx=new TransactionTemplate(transactions);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThrows(IllegalStateException.class,()->tx.execute(status->{
            long owner=actor("OWNER");
            audit.status(owner,"TEST_ROLLBACK","test:rollback",label,"before","after");
            throw new IllegalStateException("Intentional rollback");
        }));
        assertEquals(0,audit.list(0,null,"TEST_ROLLBACK",label,null,null).totalElements());
    }
}
