package com.example.android.persistence.codelab.step5_solution;

import android.os.Bundle;
import android.support.annotation.Nullable;

import com.android.support.lifecycle.LifecycleActivity;

import com.example.android.codelabs.persistence.R;
import com.example.android.persistence.codelab.step5.Product;

public class ProductReviewActivity extends LifecycleActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.product_review_activity);
        if (savedInstanceState == null) {
            ProductListFragment fragment = new ProductListFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, fragment).commit();
        }
    }

    public void show(Product product) {
        getSupportFragmentManager()
                .beginTransaction()
                .addToBackStack("product")
                .add(R.id.fragment_container,
                        ProductFragment.forProduct(product.getId()), null).commit();
    }
}
