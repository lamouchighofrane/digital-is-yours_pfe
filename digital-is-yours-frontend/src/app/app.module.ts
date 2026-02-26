import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { CommonModule } from '@angular/common';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './components/home/home.component';
import { HeaderComponent } from './components/shared/header/header.component';
import { FooterComponent } from './components/shared/footer/footer.component';
import { HeroComponent } from './components/shared/hero/hero.component';
import { FeaturesComponent } from './components/shared/features/features.component';
import { StatsComponent } from './components/shared/stats/stats.component';
import { TestimonialsComponent } from './components/shared/testimonials/testimonials.component';
import { CtaComponent } from './components/shared/cta/cta.component';
import { RegisterComponent } from './components/auth/register/register.component';
import { LoginComponent } from './components/auth/login/login.component';
import { ForgotPasswordComponent } from './components/auth/forgot-password/forgot-password.component';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { SplashScreenComponent } from './components/splashScreen/splashScreen.component';
import { VerifyEmailComponent } from './components/auth/verifyEmail/verifyEmail.component';
import { AdminLoginComponent } from './components/admin/adminlogin/adminLogin.component';
import { AdminDashboardComponent } from './components/admin/admindashboard/adminDashboard.component';
import { CategoriesComponent } from './components/admin/categories/categories.component';




@NgModule({
  declarations: [
    AppComponent, HomeComponent,
    HeaderComponent, FooterComponent, HeroComponent,
    FeaturesComponent, StatsComponent, TestimonialsComponent,
    CtaComponent, RegisterComponent, LoginComponent,
    ForgotPasswordComponent,SplashScreenComponent,VerifyEmailComponent,AdminLoginComponent,
    AdminDashboardComponent,CategoriesComponent,
  ],
  imports: [
    BrowserModule, AppRoutingModule,
    ReactiveFormsModule, HttpClientModule, CommonModule,FormsModule,
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}