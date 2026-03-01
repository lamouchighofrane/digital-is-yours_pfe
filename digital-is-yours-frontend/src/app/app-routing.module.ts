import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { RegisterComponent } from './components/auth/register/register.component';
import { LoginComponent } from './components/auth/login/login.component';
import { ForgotPasswordComponent } from './components/auth/forgot-password/forgot-password.component';
import { SplashScreenComponent } from './components/splashScreen/splashScreen.component';
import { VerifyEmailComponent } from './components/auth/verifyEmail/verifyEmail.component';
import { AdminLoginComponent } from './components/admin/adminlogin/adminLogin.component';
import { AdminDashboardComponent } from './components/admin/admindashboard/adminDashboard.component';
import { DashboardFormateurComponent } from './components/dashboard-formateur/dashboard-formateur.component';
const routes: Routes = [
  { path: '', component: SplashScreenComponent },
  { path: 'home', component: HomeComponent, title: 'Digital Is Yours' },
  { path: 'register', component: RegisterComponent, title: 'Inscription' },
  { path: 'login', component: LoginComponent, title: 'Connexion' },
  { path: 'forgot-password', component: ForgotPasswordComponent, title: 'Mot de passe oublié' },
  { path: 'verify-email', component: VerifyEmailComponent },
  { path: 'admin-login', component: AdminLoginComponent },
  { path: 'admin/dashboard', component: AdminDashboardComponent },
  { path: 'formateur/dashboard', component: DashboardFormateurComponent},
  { path: '**', redirectTo: '' },   // ← toujours EN DERNIER
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { scrollPositionRestoration: 'top' })],
  exports: [RouterModule]
})
export class AppRoutingModule {}