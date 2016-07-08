package controllers

import lila.api.Context
import lila.app._
import lila.common.{ LilaCookie, HTTPRequest }
import lila.user.{ UserRepo, User => UserModel }
import views._

import play.api.mvc._

object Soclog extends LilaController {

  val api = Env.soclog.oauth1.api
  val security = Env.security.api

  def username(oAuthId: String) = Open { implicit ctx =>
    NewUser(oAuthId) { oAuth =>
      Ok(html.soclog.username(oAuth.id, Env.security.forms.signup.soclog.fill(oAuth.profile.username))).fuccess
    }
  }

  def setUsername(oAuthId: String) = OpenBody { implicit ctx =>
    NewUser(oAuthId) { oAuth =>
      implicit val req = ctx.body
      Env.security.forms.signup.soclog.bindFromRequest.fold(
        err => BadRequest(html.soclog.username(oAuth.id, err)).fuccess,
        username => UserRepo.createSoclog(oAuth.id, username).flatMap {
          case None       => Redirect(routes.SoclogOAuth1.username(oAuth.id)).fuccess
          case Some(user) => authenticateUser(user, routes.User.show(user.username).some)
        })
    }
  }

  private def authenticateUser(u: UserModel, redirectTo: Option[Call] = none)(implicit ctx: Context): Fu[Result] = {
    implicit val req = ctx.req
    if (u.ipBan) fuccess(Redirect(routes.Lobby.home))
    else security.saveAuthentication(u.id, ctx.mobileApiVersion) map { sessionId =>
      Redirect {
        redirectTo.map(_.url) orElse
          get("referrer").filter(_.nonEmpty) orElse
          req.session.get(security.AccessUri) getOrElse
          routes.Lobby.home.url
      } withCookies LilaCookie.withSession { session =>
        session + ("sessionId" -> sessionId) - security.AccessUri - lila.soclog.oauth1.OAuth1Api.cacheKey
      }
    }
  }

  private def NewUser(oAuthId: String)(f: lila.soclog.oauth1.OAuth1 => Fu[Result])(implicit ctx: Context) =
    OptionFuResult(api findOAuth oAuthId) { oAuth =>
      UserRepo bySoclog oAuth.id flatMap {
        case None       => f(oAuth)
        case Some(user) => Redirect(routes.User.show(user.username)).fuccess
      }
    }
}